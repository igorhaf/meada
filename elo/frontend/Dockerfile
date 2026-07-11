FROM node:20-alpine

WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm ci

COPY . .

EXPOSE 3200

CMD ["npx", "next", "dev", "-H", "0.0.0.0", "-p", "3200"]
