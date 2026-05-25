FROM nginx:stable
ENV ENCORE_CLIENT_HOME=/usr/share/nginx/encoreclient
RUN mkdir -p $ENCORE_CLIENT_HOME/config && chmod 755 $ENCORE_CLIENT_HOME
COPY nginx-default.conf /etc/nginx/conf.d/default.conf
COPY . $ENCORE_CLIENT_HOME/
RUN rm -f "$ENCORE_CLIENT_HOME/nginx-default.conf" "$ENCORE_CLIENT_HOME/Dockerfile" "$ENCORE_CLIENT_HOME/.dockerignore"
