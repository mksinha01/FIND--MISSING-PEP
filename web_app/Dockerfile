# ── Stage 1: Build Web App ──
FROM node:20-alpine AS builder

WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci

COPY . .

ARG VITE_API_BASE_URL=""
ARG VITE_ENABLE_MOCK_AUTH="true"
ARG VITE_FIREBASE_API_KEY="AIzaSyAYnAkbJNRpgAqWvKjmh482QHpW_bhfmOY"
ARG VITE_FIREBASE_AUTH_DOMAIN="find-missing-pep.firebaseapp.com"
ARG VITE_FIREBASE_PROJECT_ID="find-missing-pep"
ARG VITE_FIREBASE_STORAGE_BUCKET="find-missing-pep.firebasestorage.app"
ARG VITE_FIREBASE_MESSAGING_SENDER_ID="293939241446"
ARG VITE_FIREBASE_APP_ID="1:293939241446:web:abc1a37cc6ab88e28c3627"
ARG VITE_FIREBASE_MEASUREMENT_ID="G-H309RWCPC9"

ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_ENABLE_MOCK_AUTH=$VITE_ENABLE_MOCK_AUTH
ENV VITE_FIREBASE_API_KEY=$VITE_FIREBASE_API_KEY
ENV VITE_FIREBASE_AUTH_DOMAIN=$VITE_FIREBASE_AUTH_DOMAIN
ENV VITE_FIREBASE_PROJECT_ID=$VITE_FIREBASE_PROJECT_ID
ENV VITE_FIREBASE_STORAGE_BUCKET=$VITE_FIREBASE_STORAGE_BUCKET
ENV VITE_FIREBASE_MESSAGING_SENDER_ID=$VITE_FIREBASE_MESSAGING_SENDER_ID
ENV VITE_FIREBASE_APP_ID=$VITE_FIREBASE_APP_ID
ENV VITE_FIREBASE_MEASUREMENT_ID=$VITE_FIREBASE_MEASUREMENT_ID

RUN npm run build

# ── Stage 2: Serve via Nginx Alpine ──
FROM nginx:1.25-alpine

RUN apk add --no-cache openssl && \
    mkdir -p /etc/nginx/ssl && \
    openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
    -keyout /etc/nginx/ssl/nginx.key \
    -out /etc/nginx/ssl/nginx.crt \
    -subj "/C=US/ST=State/L=City/O=FIND-MISSING-PEP/CN=65.20.80.222"

COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80 443

CMD ["nginx", "-g", "daemon off;"]
