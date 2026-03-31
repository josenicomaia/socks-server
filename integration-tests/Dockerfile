FROM alpine:3.21
RUN apk add --no-cache curl
WORKDIR /tests
COPY run-tests.sh .
RUN chmod +x run-tests.sh
ENTRYPOINT ["./run-tests.sh"]
