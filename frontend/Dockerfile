FROM node:20 AS build

WORKDIR /app

COPY package.json ./
RUN npm install

COPY . .
ARG CONFIGURATION=production
RUN npm run build -- --configuration=${CONFIGURATION}

FROM nginx:alpine

COPY --from=build /app/dist/hr-ai-frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY docker/runtime-config.template.js /opt/nevgiu/runtime-config.template.js
COPY docker/40-runtime-config.sh /docker-entrypoint.d/40-runtime-config.sh
RUN sed -i 's/\r$//' /docker-entrypoint.d/40-runtime-config.sh \
    && chmod +x /docker-entrypoint.d/40-runtime-config.sh

ENV API_URL=http://localhost:8080/api

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
