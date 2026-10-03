# syntax=docker/dockerfile:1
# Base images come from AWS's public gallery rather than Docker Hub. These are the same
# Docker Official Images, mirrored by AWS, and the move takes Docker Hub's anonymous pull
# limit off the build path: a rate-limited base image fails the build for a reason that has
# nothing to do with the change being built.
FROM public.ecr.aws/docker/library/node:22-alpine AS build
WORKDIR /app
# web-kit used to be vendored here as `COPY --from=webkit packages ./vendor/web-kit/packages`,
# followed by two `node -e` scripts that rewrote package.json to point the file: links at the
# vendored copy. brand and oidc-client are versioned dependencies now, so the lockfile resolves
# them and none of that is needed.
#
# .npmrc maps the @prabhixtechnologies scope to GitHub Packages. It holds no credential; see the
# file itself.
COPY package.json package-lock.json .npmrc ./
# `npm ci` rather than `npm install`, which is what this was — against no lockfile at all, because
# the committed lock was lockfileVersion 2 from the npm 8 on the development machine and recorded
# neither a resolved URL nor an integrity hash. Regenerated with npm 11 it is v3 and complete, so
# the image installs exactly the tree the tests ran against.
#
# The token is mounted for this layer only. GitHub Packages demands one even though the packages
# are public, and writing it into the image would put a credential in a layer that gets pushed to
# ECR, so the file is created and deleted inside a single RUN.
RUN --mount=type=secret,id=gh_token \
    printf '//npm.pkg.github.com/:_authToken=%s\n' "$(cat /run/secrets/gh_token)" > /root/.npmrc \
 && npm ci --no-audit --no-fund \
 && rm -f /root/.npmrc
COPY . .
ARG VITE_RAZORPAY_KEY_ID
ENV VITE_RAZORPAY_KEY_ID=$VITE_RAZORPAY_KEY_ID
# Blank API origin → same-origin /api via nginx (docker). Set for Vite-dev against a remote API.
ARG VITE_API_ORIGIN=
ENV VITE_API_ORIGIN=$VITE_API_ORIGIN
# Production issuer. A local stack passes http://localhost:8081 as a build arg.
# Leaving the default at localhost sends every visitor's browser to their own machine.
ARG VITE_IDENTITY_ISSUER=https://identity.prabhixtechnologies.com
ENV VITE_IDENTITY_ISSUER=$VITE_IDENTITY_ISSUER
ARG VITE_STORE_URL=
ENV VITE_STORE_URL=$VITE_STORE_URL
RUN npm run build

FROM public.ecr.aws/docker/library/nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
