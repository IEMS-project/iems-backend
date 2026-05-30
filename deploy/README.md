# Deploy selected services

Run services in this order:

```bash
docker compose -f compose-eureka-server.yml up -d
docker compose -f compose-iam-service.yml up -d
docker compose -f compose-project-service.yml up -d
docker compose -f compose-api-gateway.yml up -d
```

Before running, replace the `YOUR_*` values in each compose file with the real values for your server.

Check status:

```bash
docker ps
docker logs eureka-server
docker logs iam-service
docker logs project-service
docker logs api-gateway
```
