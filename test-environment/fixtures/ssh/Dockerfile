FROM alpine:3.22.1

RUN apk add --no-cache git openssh-server \
    && adduser -D -u 1001 fixture \
    && passwd -d fixture \
    && mkdir -p /home/fixture/.ssh /run/sshd \
    && ssh-keygen -A

COPY sshd_config /etc/ssh/sshd_config

RUN chown -R fixture:fixture /home/fixture/.ssh \
    && chmod 700 /home/fixture/.ssh

EXPOSE 22

CMD ["/usr/sbin/sshd", "-D", "-e"]
