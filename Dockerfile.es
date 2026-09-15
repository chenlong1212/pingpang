FROM docker.elastic.co/elasticsearch/elasticsearch:7.17.0
RUN bin/elasticsearch-plugin install -b https://release.infinilabs.com/analysis-ik/stable/elasticsearch-analysis-ik-7.17.0.zip
