# ---- etapa 1: gera os arquivos estaticos ----
FROM node:22-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .

# o vite le as variaveis VITE_* na hora do build e grava o valor
# dentro do bundle, entao isso precisa vir como build arg
ARG VITE_API_URL
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

# ---- etapa 2: serve os estaticos ----
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
