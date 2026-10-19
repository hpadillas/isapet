# syntax=docker/dockerfile:1

# ---- Etapa 1: compilación de Angular en modo producción ----
FROM node:20.18-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npx ng build --configuration production

# ---- Etapa 2: Nginx sirve los archivos estáticos y hace de proxy hacia la API ----
FROM nginx:1.27-alpine

# La plantilla se procesa con envsubst al iniciar el contenedor (BACKEND_URL).
COPY docker/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html

ENV BACKEND_URL=http://backend:8080

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
    CMD wget -q -O /dev/null http://127.0.0.1/healthz || exit 1
