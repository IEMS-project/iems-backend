# Deploy backend services

## GitHub Actions CI/CD

Each backend service has its own workflow in `.github/workflows` and can be run independently from the GitHub Actions UI:

```text
Deploy Eureka Server
Deploy IAM Service
Deploy Notification Service
Deploy Document Service
Deploy Project Service
Deploy Chat Service
Deploy AI Service
Deploy API Gateway
```

Required repository secrets:

```text
DOCKER_USERNAME=pthngws
DOCKER_PASSWORD=<Docker Hub token or password>
EC2_HOST=ec2-32-236-226-233.ap-southeast-2.compute.amazonaws.com
EC2_USER=ec2-user
EC2_SSH_KEY=<private key content>
```

The workflows build the selected Maven module, push `pthngws/<service>:latest` and `pthngws/<service>:<commit-sha>`, copy the service compose file to `~/iems-backend/deploy`, then recreate only that service on EC2.

Keep real env files on EC2 at `~/iems-backend/deploy`:

```text
.env.iam
.env.project
.env.document
.env.notification
.env.chat
.env.ai
```

Do not commit production secrets to this repository.

Run services in this order:

```bash
docker compose -f compose-eureka-server.yml up -d
docker compose --env-file .env.iam -f compose-iam-service.yml up -d
docker compose --env-file .env.notification -f compose-notification-service.yml up -d
docker compose --env-file .env.document -f compose-document-service.yml up -d
docker compose --env-file .env.project -f compose-project-service.yml up -d
docker compose --env-file .env.chat -f compose-chat-service.yml up -d
docker compose --env-file .env.ai -f compose-ai-service.yml up -d
docker compose -f compose-api-gateway.yml up -d
```

Before running, create service-specific env files in this `deploy` directory. Use `.env.example` as the template and fill in the real server values:

```text
.env.iam
.env.project
.env.document
.env.notification
.env.chat
.env.ai
```

Build and push images when source code changes:

```bash
docker build -t pthngws/eureka-server:latest ../eureka-server
docker build -t pthngws/iam-service:latest ../iam-service
docker build -t pthngws/notification-service:latest ../notification-service
docker build -t pthngws/document-service:latest ../document-service
docker build -t pthngws/project-service:latest ../project-service
docker build -t pthngws/chat-service:latest ../chat-service
docker build -t pthngws/ai-service:latest ../ai-service
docker build -t pthngws/api-gateway:latest ../api-gateway
```

Validate compose files before deploying:

```bash
docker compose -f compose-eureka-server.yml config
docker compose --env-file .env.iam -f compose-iam-service.yml config
docker compose --env-file .env.notification -f compose-notification-service.yml config
docker compose --env-file .env.document -f compose-document-service.yml config
docker compose --env-file .env.project -f compose-project-service.yml config
docker compose --env-file .env.chat -f compose-chat-service.yml config
docker compose --env-file .env.ai -f compose-ai-service.yml config
docker compose -f compose-api-gateway.yml config
```

Check status:

```bash
docker ps
docker logs eureka-server
docker logs iam-service
docker logs notification-service
docker logs document-service
docker logs project-service
docker logs chat-service
docker logs ai-service
docker logs api-gateway
```
