# 🎓 Learning Management System (LMS) - Backend Spring Boot

Bienvenue sur le dépôt Backend du projet **LMS** ! 
Cette API a été développée avec **Java 17**, **Spring Boot 3**, et sécurisée par **JWT** et **Spring Security**.

## 🌟 Fonctionnalités Actuelles

* **🧑‍🎓 Gestion des Rôles et Utilisateurs** : Systèmes de rôles robustes (`ADMIN`, `TEACHER`, `STUDENT`). L'administrateur peut manipuler les utilisateurs, tandis que les enseignants et étudiants ont des espaces réservés.
* **🔒 Sécurité Avancée (JWT)** : Chaque utilisateur qui se connecte reçoit un jeton crypté (Token JWT) avec une expiration automatique, protégeant toutes les données sensibles de l'application.
* **✉️ Vérification par E-mail Dynamique** : À l'inscription, un e-mail réel est envoyé à l'adresse fournie par l'utilisateur (via Gmail SMTP ou Mailtrap).
* **🔑 Mot de Passe Oublié** : Un système complet de réinitialisation de mot de passe par token chronométré (15 minutes de validité).

## 🛠️ Stack Technique

- **Langage** : Java 17
- **Framework** : Spring Boot 3.x
- **Base de données** : MySQL (Connecteur J)
- **Sécurité** : Spring Security & JSON Web Tokens (JJWT)
- **Communication** : Spring Mail (SMTP)
- **Outils** : Maven, IntelliJ IDEA, Postman pour les tests

## 🚀 Comment lancer le projet localement

1. **Prérequis** : Vous devez avoir Java 17 et MySQL d'installés sur votre ordinateur.
2. **Base de données** : Créez une base de données MySQL nommée `lms_db`.
3. **Configuration** : Allez dans le fichier `src/main/resources/application.properties` et assurez-vous de configurer votre mot de passe MySQL ainsi que vos identifiants Gmail/Mailtrap.
4. Lancer le projet via votre IDE ou utilisez la commande Maven :
   ```bash
   ./mvnw spring-boot:run
   ```
5. Le serveur démarrera par défaut sur `http://localhost:8089`.

## 📌 Architecture des Branches

- **`main`** : La branche principale stable (qui servira de socle).
- **`LMS-Backend-Spring`** : La branche de travail active où tout le code du backend est déployé et mis à jour.
- *(Future)* **`LMS-Frontend`** : La branche destinée à recevoir l'interface utilisateur web.

---
*Ce projet est en cours de développement.*
