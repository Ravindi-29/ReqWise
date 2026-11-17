# Multi-stage build for ReqWise React 18 / 19 Vite Frontend
# Stage 1: Build production bundle
FROM node:20-alpine AS builder

WORKDIR /app

# Copy dependency manifests
COPY package*.json ./

# Install dependencies
RUN npm ci || npm install

# Copy source code and build production assets
COPY . .
RUN npm run build

# Stage 2: Serve via Nginx Alpine
FROM nginx:alpine

# Remove default Nginx welcome page
RUN rm -rf /usr/share/nginx/html/*

# Copy built distribution files from builder
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy custom Nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Expose HTTP port
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
