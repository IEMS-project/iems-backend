# Deploy backend services

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
