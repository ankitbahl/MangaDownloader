FROM ubuntu:18.04
WORKDIR '/'
RUN mkdir app
WORKDIR '/app'
COPY . .
RUN apt update
RUN apt install -y openjdk-17-jdk-headless/bionic-updates imagemagick/bionic-updates
RUN sed -i 's/<policy domain="coder" rights="none" pattern="PDF" \/>/<policy domain="coder" rights="write" pattern="PDF" \/>/' /etc/ImageMagick-6/policy.xml
CMD ["tail", "-f", "/dev/null"]