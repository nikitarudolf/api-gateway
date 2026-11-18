# api-gateway

Single entry point for the Innowise microservices. Checks the JWT, proxies requests to auth-service and user-service and handles registration.

## Routes

| Path | Goes to | Token |
|---|---|---|
| `POST /api/v1/auth/register` | handled by the gateway | no |
| `POST /api/v1/auth/login`, `/refresh` | auth-service | no |
| `/api/v1/users/**`, `/api/v1/cards/**` | user-service | yes |

Everything else needs a valid access token and returns 404 if there is no route.

Registration calls auth-service to create credentials and get tokens, then creates the profile in user-service. If the profile can't be created, the credentials are deleted again so the user can retry.

## Running the whole stack

Needs only Docker. auth-service and user-service images are pulled from GHCR, the gateway is built locally.

```
cp .env.example .env
docker compose up -d --build
```

Fill in `.env` before starting. `JWT_SECRET` is a Base64 key (at least 32 bytes) and must be the same for all services. The gateway is available on http://localhost:8082, other ports are not published.

Stop with `docker compose down`, add `-v` to also remove the databases.

## Running locally

The gateway reads `.env` from the project root. By default it expects auth-service on 8081 and user-service on 8080, this can be changed with `AUTH_SERVICE_URL` and `USER_SERVICE_URL`.

## Tests

```
mvn verify
```
