# syntax=docker/dockerfile:1
FROM node:22-alpine AS build
WORKDIR /build

# The lockfile is committed, so `npm ci` is reproducible and fails loudly when it drifts from package.json.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# VITE_API_BASE_URL is baked in at build time by Vite. Empty means "same origin", which is what the
# nginx image provides, so the same image works behind any host without a rebuild.
ARG VITE_API_BASE_URL=""
ARG VITE_WS_URL=""
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL \
    VITE_WS_URL=$VITE_WS_URL

RUN npm run build

FROM nginx:1.27-alpine AS runtime

COPY --from=build /build/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=20s --timeout=4s --start-period=10s --retries=3 \
    CMD ["sh", "-c", "wget -qO- http://127.0.0.1/healthz | grep -q ok"]

CMD ["nginx", "-g", "daemon off;"]
