FROM docker.elastic.co/elasticsearch/elasticsearch:7.17.29
COPY elasticsearch-analysis-ik-7.17.29.zip /tmp/ik.zip
RUN elasticsearch-plugin install --batch file:///tmp/ik.zip && rm /tmp/ik.zip
