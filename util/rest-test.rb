#!/usr/bin/env ruby

require 'cgi'
require 'json'
require 'net/http'

def to_console(msg)
  $stderr.puts msg
end

def call_api(meth_class, path, req_cookie = nil, body = nil)
  api_host, api_port = "localhost", 8080

  req = meth_class.new(path)
  to_console("Calling API at \"#{api_host}:#{api_port}\" w/ #{meth_class} method & \"#{path}\" path.")
  unless req_cookie.nil?
    req["Cookie"] = req_cookie
    #to_console("Setting \"#{req_cookie}\" request cookie.")
  end
  unless body.nil?
    req.body = body
    to_console("Using \"#{body}\" as request body.")
  end
  resp = Net::HTTP.new(api_host, api_port).start { |http| http.request(req) }
  to_console("Server returned \"#{resp.body}\" response body.") unless resp.body.to_s.empty?

  resp_cookie = nil
  if resp.response["set-cookie"]
    resp_cookie = resp.response["set-cookie"].split(";")[0]
    to_console("Found \"#{resp_cookie}\" response cookie.")
  end
  {
    :resp => resp.body,
    :resp_core => resp.code,
    :resp_cookie => resp_cookie,
  }
end

def init_session
  session_cookie = call_api(Net::HTTP::Get, "/skream")[:resp_cookie]
  to_console("Initialized \"#{session_cookie}\" session.")
  session_cookie
end

def get_skreams(session_cookie)
  call_api(Net::HTTP::Get, "/skream", session_cookie)
end

def create_skream(session_cookie)
  skream_id = call_api(Net::HTTP::Post, "/skream", session_cookie)[:resp]
  to_console("Created Skream w/ \"#{skream_id}\" ID.")
  skream_id
end

def track_stat(session_cookie, skream_id, stat, *args)
  body_els = ["stat=#{CGI.escape(stat)}"]
  args.each_index { |i| body_els << "arg#{i+1}=#{CGI.escape(args[i].to_s)}" }
  body = body_els.join("&")
  call_api(Net::HTTP::Post, "/track/#{CGI.escape(skream_id)}", session_cookie, body)[:resp]
end

def get_skream(session_cookie, skream_id)
  call_api(Net::HTTP::Get, "/skream/#{CGI.escape(skream_id)}", session_cookie)
end

def add_num(session_cookie, skream_id, x)
  call_api(Net::HTTP::Put, "/skream/#{CGI.escape(skream_id)}", session_cookie, "x=#{CGI.escape(x.to_s)}")
end

def delete_skream(session_cookie, skream_id)
  call_api(Net::HTTP::Delete, "/skream/#{CGI.escape(skream_id)}", session_cookie)
end

session_cookie = init_session

skream_id1 = create_skream(session_cookie)
skream_id2 = create_skream(session_cookie)
track_stat(session_cookie, skream_id1, "min")
track_stat(session_cookie, skream_id1, "moment", 3)
track_stat(session_cookie, skream_id2, "min")
srv_skream_ids = JSON.parse(get_skreams(session_cookie)[:resp])
to_console("Found #{srv_skream_ids.inspect} Skream IDs for this session.")
raise "Skream ID mismatch" unless [skream_id1, skream_id2].sort == srv_skream_ids.sort

100.times do
  x = rand * 1_000_000
  add_num(session_cookie, (rand < 0.5) ? skream_id1 : skream_id2, x)
end
sk1 = get_skream(session_cookie, skream_id1)
sk2 = get_skream(session_cookie, skream_id2)
to_console("Found first \"#{sk1}\" Skream w/ \"#{skream_id1}\" ID.")
to_console("Found second \"#{sk2}\" Skream w/ \"#{skream_id2}\" ID.")

delete_skream(session_cookie, skream_id2)
srv_skream_ids = JSON.parse(get_skreams(session_cookie)[:resp])
to_console("Found #{srv_skream_ids.inspect} Skream IDs for this session, after deleting.")

