docker compose -f docker-compose.dev.yml down && ./package-testing-backend.sh && docker compose -f docker-compose.dev.yml build && docker compose -f docker-compose.dev.yml up -d
