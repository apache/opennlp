FROM openjdk:11-jre-slim
MAINTAINER Apache OpenNLP (dev@opennlp.apache.org)

ARG OPENNLP_BINARY

ENV OPENNLP_BASE_DIR /opt/opennlp

ADD $OPENNLP_BINARY $OPENNLP_BASE_DIR

CMD ["sh"]
