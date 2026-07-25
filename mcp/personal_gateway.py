#!/usr/bin/env python3
"""Long-running local Streamable HTTP surface for the personal MCP gateway."""
import json
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from watch_intervals_mcp import handle_message

HOST="127.0.0.1"
PORT=8767

class Handler(BaseHTTPRequestHandler):
    server_version="WatchIntervalsGateway/0.6"
    def do_GET(self):
        if self.path in {"/healthz","/readyz"}:
            self.send_json(200,{"state":"ok","gateway":"online"})
        else:self.send_json(404,{"error":"not_found"})
    def do_POST(self):
        if self.path!="/mcp":self.send_json(404,{"error":"not_found"});return
        try:
            length=min(int(self.headers.get("Content-Length","0")),1_000_000)
            message=json.loads(self.rfile.read(length).decode("utf-8"))
            result=handle_message(message)
            if result is None:self.send_response(202);self.end_headers()
            else:self.send_json(200,result)
        except Exception as error:self.send_json(400,{"jsonrpc":"2.0","id":None,"error":{"code":-32700,"message":str(error)}})
    def send_json(self,status,value):
        data=json.dumps(value,ensure_ascii=False,separators=(",",":")).encode("utf-8")
        self.send_response(status);self.send_header("Content-Type","application/json; charset=utf-8");self.send_header("Content-Length",str(len(data)));self.end_headers();self.wfile.write(data)
    def log_message(self,format,*args): pass

if __name__=="__main__":ThreadingHTTPServer((HOST,PORT),Handler).serve_forever()
