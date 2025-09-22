package com.iems.iamservice.config;

import com.iems.iamservice.entity.Account;
import com.iems.iamservice.entity.Permission;
import com.iems.iamservice.entity.Role;
import com.iems.iamservice.entity.UserRole;
import com.iems.iamservice.repository.AccountRepository;
import com.iems.iamservice.repository.PermissionRepository;
import com.iems.iamservice.repository.RoleRepository;
import com.iems.iamservice.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final AccountRepository accountRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("Starting data seeding...");
        
        // Seed permissions
        seedPermissions();
        
        // Seed roles
        seedRoles();
        
        // Seed admin account
        seedAdminAccount();
        
        log.info("Data seeding completed successfully!");
    }

    private void seedPermissions() {
        log.info("Seeding permissions...");
        
        List<Permission> permissions = Arrays.asList(
            // User Management Permissions
            Permission.builder()
                .code("USER_CREATE")
                .name("Create User")
                .description("Permission to create new users")
                .active(true)
                .build(),
            Permission.builder()
                .code("USER_READ")
                .name("Read User")
                .description("Permission to view user information")
                .active(true)
                .build(),
            Permission.builder()
                .code("USER_UPDATE")
                .name("Update User")
                .description("Permission to update user information")
                .active(true)
                .build(),
            Permission.builder()
                .code("USER_DELETE")
                .name("Delete User")
                .description("Permission to delete users")
                .active(true)
                .build(),
            
            // Project Management Permissions
            Permission.builder()
                .code("PROJECT_CREATE")
                .name("Create Project")
                .description("Permission to create new projects")
                .active(true)
                .build(),
            Permission.builder()
                .code("PROJECT_READ")
                .name("Read Project")
                .description("Permission to view project information")
                .active(true)
                .build(),
            Permission.builder()
                .code("PROJECT_UPDATE")
                .name("Update Project")
                .description("Permission to update project information")
                .active(true)
                .build(),
            Permission.builder()
                .code("PROJECT_DELETE")
                .name("Delete Project")
                .description("Permission to delete projects")
                .active(true)
                .build(),
            Permission.builder()
                .code("PROJECT_MANAGE")
                .name("Manage Project")
                .description("Permission to manage project members and settings")
                .active(true)
                .build(),
            
            // Task Management Permissions
            Permission.builder()
                .code("TASK_CREATE")
                .name("Create Task")
                .description("Permission to create new tasks")
                .active(true)
                .build(),
            Permission.builder()
                .code("TASK_READ")
                .name("Read Task")
                .description("Permission to view task information")
                .active(true)
                .build(),
            Permission.builder()
                .code("TASK_UPDATE")
                .name("Update Task")
                .description("Permission to update task information")
                .active(true)
                .build(),
            Permission.builder()
                .code("TASK_DELETE")
                .name("Delete Task")
                .description("Permission to delete tasks")
                .active(true)
                .build(),
            Permission.builder()
                .code("TASK_ASSIGN")
                .name("Assign Task")
                .description("Permission to assign tasks to users")
                .active(true)
                .build(),
            
            // Department Management Permissions
            Permission.builder()
                .code("DEPT_CREATE")
                .name("Create Department")
                .description("Permission to create new departments")
                .active(true)
                .build(),
            Permission.builder()
                .code("DEPT_READ")
                .name("Read Department")
                .description("Permission to view department information")
                .active(true)
                .build(),
            Permission.builder()
                .code("DEPT_UPDATE")
                .name("Update Department")
                .description("Permission to update department information")
                .active(true)
                .build(),
            Permission.builder()
                .code("DEPT_DELETE")
                .name("Delete Department")
                .description("Permission to delete departments")
                .active(true)
                .build(),
            Permission.builder()
                .code("DEPT_MANAGE")
                .name("Manage Department")
                .description("Permission to manage department members")
                .active(true)
                .build(),
            
            // Document Management Permissions
            Permission.builder()
                .code("DOC_CREATE")
                .name("Create Document")
                .description("Permission to create new documents")
                .active(true)
                .build(),
            Permission.builder()
                .code("DOC_READ")
                .name("Read Document")
                .description("Permission to view documents")
                .active(true)
                .build(),
            Permission.builder()
                .code("DOC_UPDATE")
                .name("Update Document")
                .description("Permission to update documents")
                .active(true)
                .build(),
            Permission.builder()
                .code("DOC_DELETE")
                .name("Delete Document")
                .description("Permission to delete documents")
                .active(true)
                .build(),
            Permission.builder()
                .code("DOC_SHARE")
                .name("Share Document")
                .description("Permission to share documents with others")
                .active(true)
                .build(),
            
            // System Administration Permissions
            Permission.builder()
                .code("ADMIN_1")
                .name("System Administrator")
                .description("Full system administration access")
                .active(true)
                .build(),
            Permission.builder()
                .code("ADMIN_2")
                .name("User Administrator")
                .description("User management administration access")
                .active(true)
                .build(),
            Permission.builder()
                .code("ADMIN_3")
                .name("Project Administrator")
                .description("Project management administration access")
                .active(true)
                .build(),
            
            // Reporting Permissions
            Permission.builder()
                .code("REPORT_READ")
                .name("Read Reports")
                .description("Permission to view reports")
                .active(true)
                .build(),
            Permission.builder()
                .code("REPORT_CREATE")
                .name("Create Reports")
                .description("Permission to create reports")
                .active(true)
                .build(),
            Permission.builder()
                .code("REPORT_EXPORT")
                .name("Export Reports")
                .description("Permission to export reports")
                .active(true)
                .build()
        );

        for (Permission permission : permissions) {
            if (!permissionRepository.existsByCode(permission.getCode())) {
                permissionRepository.save(permission);
                log.info("Created permission: {}", permission.getCode());
            }
        }
    }

    private void seedRoles() {
        log.info("Seeding roles...");
        
        // Get all permissions
        List<Permission> allPermissions = permissionRepository.findAll();
        
        // Create Super Admin Role
        Role superAdminRole = Role.builder()
            .code("SUPER_ADMIN")
            .name("Super Administrator")
            .description("Full system access with all permissions")
            .active(true)
            .build();
        
        if (!roleRepository.existsByCode("SUPER_ADMIN")) {
            superAdminRole = roleRepository.save(superAdminRole);
            // Add all permissions to super admin
            for (Permission permission : allPermissions) {
                superAdminRole.addPermission(permission);
            }
            roleRepository.save(superAdminRole);
            log.info("Created role: SUPER_ADMIN with {} permissions", allPermissions.size());
        }
        
        // Create Admin Role
        Role adminRole = Role.builder()
            .code("ADMIN")
            .name("Administrator")
            .description("System administrator with most permissions")
            .active(true)
            .build();
        
        if (!roleRepository.existsByCode("ADMIN")) {
            adminRole = roleRepository.save(adminRole);
            // Add admin permissions (exclude SUPER_ADMIN specific permissions)
            for (Permission permission : allPermissions) {
                if (!permission.getCode().equals("ADMIN_1")) {
                    adminRole.addPermission(permission);
                }
            }
            roleRepository.save(adminRole);
            log.info("Created role: ADMIN");
        }
        
        // Create Project Manager Role
        Role projectManagerRole = Role.builder()
            .code("PROJECT_MANAGER")
            .name("Project Manager")
            .description("Project management role with project and task permissions")
            .active(true)
            .build();
        
        if (!roleRepository.existsByCode("PROJECT_MANAGER")) {
            projectManagerRole = roleRepository.save(projectManagerRole);
            // Add project and task related permissions
            List<String> projectManagerPermissions = Arrays.asList(
                "PROJECT_CREATE", "PROJECT_READ", "PROJECT_UPDATE", "PROJECT_MANAGE",
                "TASK_CREATE", "TASK_READ", "TASK_UPDATE", "TASK_ASSIGN",
                "USER_READ", "DEPT_READ", "DOC_CREATE", "DOC_READ", "DOC_UPDATE", "DOC_SHARE",
                "REPORT_READ", "REPORT_CREATE", "REPORT_EXPORT"
            );
            
            for (Permission permission : allPermissions) {
                if (projectManagerPermissions.contains(permission.getCode())) {
                    projectManagerRole.addPermission(permission);
                }
            }
            roleRepository.save(projectManagerRole);
            log.info("Created role: PROJECT_MANAGER");
        }
        
        // Create Team Lead Role
        Role teamLeadRole = Role.builder()
            .code("TEAM_LEAD")
            .name("Team Lead")
            .description("Team leadership role with limited management permissions")
            .active(true)
            .build();
        
        if (!roleRepository.existsByCode("TEAM_LEAD")) {
            teamLeadRole = roleRepository.save(teamLeadRole);
            // Add team lead permissions
            List<String> teamLeadPermissions = Arrays.asList(
                "PROJECT_READ", "TASK_CREATE", "TASK_READ", "TASK_UPDATE", "TASK_ASSIGN",
                "USER_READ", "DEPT_READ", "DOC_CREATE", "DOC_READ", "DOC_UPDATE", "DOC_SHARE",
                "REPORT_READ", "REPORT_CREATE"
            );
            
            for (Permission permission : allPermissions) {
                if (teamLeadPermissions.contains(permission.getCode())) {
                    teamLeadRole.addPermission(permission);
                }
            }
            roleRepository.save(teamLeadRole);
            log.info("Created role: TEAM_LEAD");
        }
        
        // Create Developer Role
        Role developerRole = Role.builder()
            .code("DEVELOPER")
            .name("Developer")
            .description("Developer role with basic project and task permissions")
            .active(true)
            .build();
        
        if (!roleRepository.existsByCode("DEVELOPER")) {
            developerRole = roleRepository.save(developerRole);
            // Add developer permissions
            List<String> developerPermissions = Arrays.asList(
                "PROJECT_READ", "TASK_READ", "TASK_UPDATE",
                "USER_READ", "DEPT_READ", "DOC_READ", "DOC_UPDATE",
                "REPORT_READ"
            );
            
            for (Permission permission : allPermissions) {
                if (developerPermissions.contains(permission.getCode())) {
                    developerRole.addPermission(permission);
                }
            }
            roleRepository.save(developerRole);
            log.info("Created role: DEVELOPER");
        }
        
        // Create User Role
        Role userRole = Role.builder()
            .code("USER")
            .name("User")
            .description("Basic user role with read-only permissions")
            .active(true)
            .build();
        
        if (!roleRepository.existsByCode("USER")) {
            userRole = roleRepository.save(userRole);
            // Add basic user permissions
            List<String> userPermissions = Arrays.asList(
                "PROJECT_READ", "TASK_READ",
                "USER_READ", "DEPT_READ", "DOC_READ",
                "REPORT_READ"
            );
            
            for (Permission permission : allPermissions) {
                if (userPermissions.contains(permission.getCode())) {
                    userRole.addPermission(permission);
                }
            }
            roleRepository.save(userRole);
            log.info("Created role: USER");
        }
    }

    private void seedAdminAccount() {
        log.info("Seeding admin account...");
        
        // Check if admin account already exists
        if (accountRepository.existsByUsername("admin")) {
            log.info("Admin account already exists, skipping...");
            return;
        }
        
        // Create admin user ID (this should match with User Service)
        UUID adminUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        
        // Create admin account
        Account adminAccount = Account.builder()
            .userId(adminUserId)
            .username("admin")
            .email("admin@iems.com")
            .passwordHash(passwordEncoder.encode("admin123"))
            .enabled(true)
            .build();
        
        adminAccount = accountRepository.save(adminAccount);
        log.info("Created admin account: {}", adminAccount.getUsername());
        
        // Get Super Admin role
        Role superAdminRole = roleRepository.findByCode("SUPER_ADMIN")
            .orElseThrow(() -> new RuntimeException("Super Admin role not found"));
        
        // Assign Super Admin role to admin account
        UserRole userRole = UserRole.builder()
            .userId(adminUserId)
            .role(superAdminRole)
            .active(true)
            .build();
        
        userRoleRepository.save(userRole);
        log.info("Assigned SUPER_ADMIN role to admin account");
        
        // Also create a regular admin account
        UUID regularAdminUserId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        
        if (!accountRepository.existsByUsername("admin2")) {
            Account regularAdminAccount = Account.builder()
                .userId(regularAdminUserId)
                .username("admin2")
                .email("admin2@iems.com")
                .passwordHash(passwordEncoder.encode("admin123"))
                .enabled(true)
                .build();
            
            regularAdminAccount = accountRepository.save(regularAdminAccount);
            log.info("Created regular admin account: {}", regularAdminAccount.getUsername());
            
            // Get Admin role
            Role adminRole = roleRepository.findByCode("ADMIN")
                .orElseThrow(() -> new RuntimeException("Admin role not found"));
            
            // Assign Admin role to regular admin account
            UserRole regularAdminUserRole = UserRole.builder()
                .userId(regularAdminUserId)
                .role(adminRole)
                .active(true)
                .build();
            
            userRoleRepository.save(regularAdminUserRole);
            log.info("Assigned ADMIN role to regular admin account");
        }
    }
}

