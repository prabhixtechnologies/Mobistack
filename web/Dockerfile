# syntax=docker/dockerfile:1
# Base images come from AWS's public gallery rather than Docker Hub. These are the same
# Docker Official Images, mirrored by AWS, and the move takes Docker Hub's anonymous pull
# limit off the build path: a rate-limited base image fails the build for a reason that has
# nothing to do with the change being built.
FROM public.ecr.aws/docker/library/node:22-alpine AS build
WORKDIR /app
# Vendor file: packages under /app so Vite can resolve their peer deps from /app/node_modules.
COPY --from=webkit packages ./vendor/web-kit/packages
COPY package.json ./
# Host lockfiles are npm 8 / lockfileVersion 2. node:22-alpine ships npm 10, which
# rejects those on `npm ci` and crashes on `npm install` if the old lock is present.
RUN node -e "const fs=require('fs');const p=JSON.parse(fs.readFileSync('package.json','utf8'));const m={'@prabhix/brand':'file:./vendor/web-kit/packages/brand','@prabhix/oidc-client':'file:./vendor/web-kit/packages/oidc-client'};for (const [k,v] of Object.entries(m)){if(p.dependencies?.[k]?.startsWith('file:'))p.dependencies[k]=v;}fs.writeFileSync('package.json',JSON.stringify(p,null,2));"
RUN npm install --no-audit --no-fund
COPY . .
RUN node -e "const fs=require('fs');const p=JSON.parse(fs.readFileSync('package.json','utf8'));const m={'@prabhix/brand':'file:./vendor/web-kit/packages/brand','@prabhix/oidc-client':'file:./vendor/web-kit/packages/oidc-client'};for (const [k,v] of Object.entries(m)){if(p.dependencies?.[k]?.startsWith('file:'))p.dependencies[k]=v;}fs.writeFileSync('package.json',JSON.stringify(p,null,2));"
ARG VITE_RAZORPAY_KEY_ID
ENV VITE_RAZORPAY_KEY_ID=$VITE_RAZORPAY_KEY_ID
# Blank API origin → same-origin /api via nginx (docker). Set for Vite-dev against a remote API.
ARG VITE_API_ORIGIN=
ENV VITE_API_ORIGIN=$VITE_API_ORIGIN
ARG VITE_IDENTITY_ISSUER=http://localhost:8081
ENV VITE_IDENTITY_ISSUER=$VITE_IDENTITY_ISSUER
ARG VITE_STORE_URL=
ENV VITE_STORE_URL=$VITE_STORE_URL
RUN npm run build

FROM public.ecr.aws/docker/library/nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
