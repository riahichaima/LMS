# Répartiteur de charge : deux backends, au plus bas coût

Le backend est sans session (JWT) : il peut tourner en plusieurs exemplaires derrière **Caddy**, qui
devient répartiteur de charge. Rien ne change tant que ce dossier n'est pas activé.

```
                        VM 1 (existante, B2als_v2)                       VM 2 (nouvelle, B2als_v2)
Internet ── 443 ──► Caddy ─┬─► backend-1 ──┐                      ┌──► backend-2
                           └──────────────────────── réseau privé ┘
                    MySQL · service IA · Redis · dossier uploads (NFS) ◄──── backend-2
```

| Ce qui était en mémoire d'un seul backend | Avec deux backends |
|---|---|
| Cache du catalogue (Caffeine) | Chaque backend garde le sien ; un changement est annoncé par Redis et les deux le vident |
| Notifications temps réel (WebSocket) | Publiées dans Redis ; le backend qui tient la connexion de l'utilisateur la remet |
| Salles de visio | Connexions `/ws` toujours sur backend-1 (backend-2 en secours) ; nombre de participants copié dans Redis |
| Limites IA (par heure) | Comptées dans Redis, valables pour l'ensemble des backends |
| Limite des essais de connexion | `/auth/*` : un même réseau va toujours au même backend (`client_ip_hash`) |
| Tâches planifiées (envoi des copies, rappels) | Verrou dans MySQL (`scheduled_job_lock`) : un seul backend les lance |
| Fichiers des cours | Dossier `uploads` de la VM 1, monté en NFS sur la VM 2 |

Code : `backend/src/main/java/com/course/lms/cluster/` (actif avec `APP_CLUSTER_ENABLED=true`).
Tests : `ClusterRedisIntegrationTest` (deux backends simulés, vrai Redis), `ClusterContextIntegrationTest`,
`JobLockIntegrationTest` ; la CI lance Redis pour cela.

## Coût

| Élément | Prix indicatif (Sweden Central) |
|---|---|
| VM 2 `Standard_B2als_v2` (2 vCPU, 4 Go) + disque Standard SSD 30 Go | ≈ 28 $ + ≈ 2,5 $ par mois |
| Redis, NFS, répartiteur (Caddy) | 0 $ (sur la VM 1) |
| **Total en plus** | **≈ 30 $ par mois** ; VM 2 arrêtée (désallouée) = seulement le disque |

Vérifier dans le calculateur Azure avant de créer la VM.

## Démonstration locale (aucun coût)

```bash
cd backend && ./mvnw -q -DskipTests package
cd ../deploy/cluster && docker compose -f docker-compose.demo.yml up -d
curl -si http://localhost:8090/livez | grep X-Backend      # backend-1, backend-2, backend-1…
docker compose -f docker-compose.demo.yml stop backend-1     # panne : tout passe par backend-2
docker compose -f docker-compose.demo.yml down -v            # tout effacer
```

## Mise en production (portail Azure)

### 1. Créer la VM 2
- Même groupe de ressources, même **réseau virtuel et sous-réseau** que la VM 1.
- Ubuntu 24.04, taille **Standard_B2als_v2**, disque Standard SSD 30 Go.
- **Pas d'adresse IP publique** : elle n'est joignable que par la VM 1 ; administration par « Exécuter la commande ».
- Noter les adresses privées (ex. VM 1 = 10.0.0.4, VM 2 = 10.0.0.5).

### 2. Ouvrir le réseau privé (groupes de sécurité réseau)
- **VM 1**, entrant depuis la seule adresse de la VM 2 : TCP 3306 (MySQL), 6379 (Redis), 8000 (IA), 2049 (NFS).
- **VM 2**, entrant depuis la seule adresse de la VM 1 : TCP 8090 (backend).
- Rien d'autre : Caddy (80/443) reste la seule entrée depuis Internet.

### 3. Partager le dossier des fichiers (NFS)
VM 1 :
```bash
sudo apt-get install -y nfs-kernel-server
echo "/var/lib/docker/volumes/lms_uploads/_data 10.0.0.5(rw,sync,no_subtree_check)" | sudo tee -a /etc/exports
sudo exportfs -ra
```
VM 2 :
```bash
curl -fsSL https://get.docker.com | sudo sh
sudo apt-get install -y nfs-common
sudo mkdir -p /mnt/lms-uploads
echo "10.0.0.4:/var/lib/docker/volumes/lms_uploads/_data /mnt/lms-uploads nfs defaults,_netdev 0 0" | sudo tee -a /etc/fstab
sudo mount -a && ls /mnt/lms-uploads
```

### 4. Activer sur la VM 1
Sauvegarde d'abord (`./backup.sh`), puis dans `deploy/.env` :
```
COMPOSE_FILE=docker-compose.yml:cluster/docker-compose.vm1.yml
PRIVATE_IP=10.0.0.4
BACKEND_2=10.0.0.5:8090
REDIS_PASSWORD=<openssl rand -hex 24>
```
puis `./update.sh` (Redis démarre, MySQL et l'IA écoutent aussi sur l'adresse privée, Caddy répartit).
Tant que la VM 2 ne répond pas, Caddy l'écarte : tout passe par backend-1.

### 5. Démarrer la VM 2
```bash
sudo git clone https://github.com/riahichaima/LMS.git /opt/lms/LMS
cd /opt/lms/LMS/deploy/cluster && sudo cp vm2.env.example .env   # remplir : mêmes secrets que la VM 1
sudo docker compose -f docker-compose.vm2.yml up -d
```

### 6. Vérifier
```bash
for i in 1 2 3 4; do curl -s -D - -o /dev/null https://<API_DOMAIN>/livez | grep -i x-backend; done
```
Les réponses alternent entre les deux backends. Arrêter le backend de la VM 2 : le site continue.

### Mises à jour
VM 1 d'abord (`./update.sh`), puis VM 2 (`docker compose -f docker-compose.vm2.yml pull && … up -d`) :
un backend reste toujours en service.

### Revenir à un seul backend
Retirer la ligne `COMPOSE_FILE` de `deploy/.env` sur la VM 1, `./update.sh`, puis arrêter (désallouer) la VM 2.

## Limites connues
- La VM 1 reste un point unique (base, Redis, IA, Caddy) : ce montage ajoute de la **capacité** et tolère la
  panne d'un backend, pas celle de la VM 1. Étape suivante : Azure Load Balancer + MySQL géré (≈ 100 $/mois).
- Le plafond de 50 essais de connexion par compte, tous réseaux confondus, est compté par backend.
- Si backend-1 tombe pendant une visio, les participants se reconnectent sur backend-2.
