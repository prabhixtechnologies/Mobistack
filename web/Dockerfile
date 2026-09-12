# Base images come from AWS's public gallery rather than Docker Hub. These are the same
# Docker Official Images, mirrored by AWS, and the move takes Docker Hub's anonymous pull
# limit off the build path: a rate-limited base image fails the build for a reason that has
# nothing to do with the change being built.
FROM public.ecr.aws/docker/library/node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
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
