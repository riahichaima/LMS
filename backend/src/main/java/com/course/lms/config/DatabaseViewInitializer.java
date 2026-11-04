package com.course.lms.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "lms.database.views", name = "enabled", havingValue = "true", matchIfMissing = false)
public class DatabaseViewInitializer implements CommandLineRunner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        createUsersWithRolesView();
    }

    private void createUsersWithRolesView() {
        try {
            // Créer une vue qui combine users et roles
            String createViewSQL = """
                CREATE OR REPLACE VIEW users_with_roles AS
                SELECT 
                    u.id as user_id,
                    u.name as user_name,
                    u.email as user_email,
                    u.password as user_password,
                    r.id as role_id,
                    r.name as role_name
                FROM users u
                LEFT JOIN roles r ON u.role_id = r.id
                ORDER BY r.name, u.id
                """;

            jdbcTemplate.execute(createViewSQL);
            System.out.println("✅ Created view 'users_with_roles' successfully");

            // Créer une vue qui ressemble à une table "roles" avec tous les utilisateurs
            String createRolesViewSQL = """
                CREATE OR REPLACE VIEW roles_with_users AS
                SELECT 
                    r.id as role_id,
                    r.name as role_name,
                    u.id as user_id,
                    u.name as user_name,
                    u.email as user_email
                FROM roles r
                LEFT JOIN users u ON r.id = u.role_id
                ORDER BY r.name, u.id
                """;

            jdbcTemplate.execute(createRolesViewSQL);
            System.out.println("✅ Created view 'roles_with_users' successfully");

            // Afficher un résumé
            String countSQL = """
                SELECT 
                    r.name as role_name,
                    COUNT(u.id) as user_count,
                    GROUP_CONCAT(u.id ORDER BY u.id) as user_ids
                FROM roles r
                LEFT JOIN users u ON r.id = u.role_id
                GROUP BY r.id, r.name
                ORDER BY r.name
                """;

            var results = jdbcTemplate.queryForList(countSQL);
            System.out.println("\n📊 RÉSUMÉ DES UTILISATEURS PAR RÔLE:");
            for (var row : results) {
                System.out.println("Rôle " + row.get("role_name") + ": " + 
                                 row.get("user_count") + " utilisateurs (IDs: " + 
                                 row.get("user_ids") + ")");
            }

        } catch (Exception e) {
            System.err.println("❌ Error creating views: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
