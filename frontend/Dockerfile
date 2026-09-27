# syntax=docker/dockerfile:1
#   docker build -f frontend/Dockerfile frontend

FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# nginx sirve la SPA y hace de proxy de /api hacia el backend: front y API quedan en el MISMO origen,
# así la cookie HttpOnly de refresh es first-party (ADR-0004).
FROM nginx:1.29-alpine
ENV PORT=80 \
    API_UPSTREAM=http://api:8080
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
