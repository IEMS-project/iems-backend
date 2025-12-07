# GitHub Repository Management Feature

## Overview

This feature allows projects to manage multiple GitHub repositories, providing an integrated code viewer directly within the application.

## Features

### Backend Components

1. **Entity**: `ProjectRepository`

   - Fields: id, projectId, name, repoLink, createdAt, updatedAt
   - Location: `src/main/java/com/iems/projectservice/entity/ProjectRepository.java`

2. **Repository**: `ProjectRepositoryRepository`

   - Spring Data JPA repository for CRUD operations
   - Location: `src/main/java/com/iems/projectservice/repository/ProjectRepositoryRepository.java`

3. **DTOs**:

   - `CreateProjectRepositoryDto`: For creating new repositories
   - `UpdateProjectRepositoryDto`: For updating existing repositories
   - `ProjectRepositoryDto`: Response DTO
   - Location: `src/main/java/com/iems/projectservice/dto/`

4. **Service**: `ProjectRepositoryService`

   - Business logic for repository management
   - Location: `src/main/java/com/iems/projectservice/service/ProjectRepositoryService.java`

5. **Controller**: `ProjectRepositoryController`
   - REST endpoints for repository operations
   - Base path: `/api/project-repositories`
   - Location: `src/main/java/com/iems/projectservice/controller/ProjectRepositoryController.java`

### API Endpoints

- `POST /api/project-repositories` - Create new repository
- `GET /api/project-repositories/project/{projectId}` - Get all repositories for a project
- `GET /api/project-repositories/{id}` - Get repository by ID
- `PUT /api/project-repositories/{id}` - Update repository
- `DELETE /api/project-repositories/{id}` - Delete repository
- `DELETE /api/project-repositories/project/{projectId}` - Delete all repositories for a project

### Frontend Components

1. **Service**: `githubService.js`

   - GitHub API integration
   - Token management
   - Repository CRUD operations
   - Location: `src/services/githubService.js`

2. **Component**: `ProjectCode.jsx`
   - Repository selection
   - GitHub token management
   - Code viewer with syntax highlighting
   - Branch and commit navigation
   - Location: `src/components/project/ProjectCode.jsx`

## Usage

### Setting Up

1. **Database Migration**
   Run the SQL migration file to create the `project_repositories` table:

   ```sql
   -- File: database-migration-repositories.sql
   ```

2. **GitHub Personal Access Token**
   - Go to: https://github.com/settings/tokens
   - Generate new token (classic)
   - Select scope: `repo` (Full control of private repositories)
   - Copy the generated token

### Using the Feature

1. **Navigate to Project Code Tab**

   - Go to any project detail page
   - Click on the "Code" tab

2. **Add GitHub Token** (First time)

   - Click "Add GitHub Token" button
   - Paste your GitHub Personal Access Token
   - Click "Save Token"
   - Token is stored in browser localStorage

3. **Add Repository**

   - Click "Add Repo" button
   - Enter repository name (e.g., "Backend API")
   - Enter GitHub repository URL (e.g., "https://github.com/owner/repo")
   - Click "Add Repository"

4. **Browse Code**

   - Select a repository from the dropdown
   - Browse the file tree on the left
   - Click on files to view their content
   - Use branch selector to switch branches
   - View commit history with the "History" button

5. **Manage Repositories**
   - Select different repositories from the dropdown
   - Click trash icon to delete a repository
   - Click "Open in GitHub" to view on GitHub

## Features in Code Viewer

- **Syntax Highlighting**: Supports Java, JavaScript, TypeScript, Python, JSON, YAML, Markdown, SQL, CSS, and more
- **Line Numbers**: Each line is numbered for easy reference
- **Branch Navigation**: Switch between branches
- **Commit History**: View and navigate to specific commits
- **Copy Code**: Copy file content with one click
- **Dark/Light Mode**: Automatically adapts to theme
- **File Tree**: Expandable folder structure
- **Direct GitHub Links**: Quick access to GitHub repository

## Security Notes

- GitHub tokens are stored in browser localStorage
- Tokens are sent with `Authorization: token <token>` header
- No tokens are stored on the backend
- Users should generate tokens with minimal required permissions
- Consider using fine-grained personal access tokens for better security

## Database Schema

```sql
CREATE TABLE project_repositories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    repo_link VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_project_id (project_id),
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);
```

## GitHub API Rate Limits

- **Without Token**: 60 requests per hour
- **With Token**: 5,000 requests per hour
- Rate limit information is included in API response headers

## Future Enhancements

- [ ] Support for multiple token management (different tokens per repo)
- [ ] Repository sync status
- [ ] Code search within repository
- [ ] Pull request viewer
- [ ] Issue tracker integration
- [ ] Webhook integration for automatic updates
- [ ] Code diff viewer
- [ ] Repository statistics and insights
