raspivid -o - -t 9999999 -w 1280 -h 720 -vf  | cvlc -vvv stream:///dev/stdin --sout '#standard{access=http,mux=ts,dst=:8080}' :demux=h264 