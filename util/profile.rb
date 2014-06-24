#!/usr/bin/env ruby

require 'rubygems'
require 'pg'

N = 1_000_000

def get_memory_usage(pid)
  mem = 0
  smaps = `cat /proc/#{pid}/smaps`
  smaps.split("\n").each do |smap_line|
    next unless smap_line =~ /^Rss:\s*([.0-9]+)\s+kB/
    mem += $1.to_i * 1024
  end
  sprintf("%0.2f", mem.to_f / (2 ** 20)).to_f
end

def each_random_number
  n = N
  n += 1 unless n.odd?
  rand_key = 12345
  srand(rand_key)
  n.times { |i| yield(i, (rand * 1_000_000).to_i) }
end

def to_console(msg)
  $stdout.puts "[#{Time.now}] #{msg}"
end

xs = []
each_random_number { |i, x| xs << x }
actual_median = xs.sort[xs.length / 2]
to_console "Median is #{actual_median} exactly."

conn = PG.connect(
  :host => "localhost", :port => 5432,
  :dbname => "skream", :user => "skream", :password => "skream")

# PostgreSQL median aggregate function from "https://wiki.postgresql.org/wiki/Aggregate_Median":
sql = <<'EOF_SQL'
  DROP TABLE IF EXISTS xs;
  CREATE TABLE xs (x INTEGER);
  CREATE INDEX x_idx ON xs (x);

  CREATE OR REPLACE FUNCTION _final_median(numeric[])
     RETURNS numeric AS
  $$
     SELECT AVG(val)
     FROM (
       SELECT val
       FROM unnest($1) val
       ORDER BY 1
       LIMIT  2 - MOD(array_upper($1, 1), 2)
       OFFSET CEIL(array_upper($1, 1) / 2.0) - 1
     ) sub;
  $$
  LANGUAGE 'sql' IMMUTABLE;

  DROP AGGREGATE IF EXISTS median(numeric);
  CREATE AGGREGATE median(numeric) (
    SFUNC=array_append,
    STYPE=numeric[],
    FINALFUNC=_final_median,
    INITCOND='{}'
  );
EOF_SQL
conn.exec(sql)

conn.exec("BEGIN TRANSACTION;")
each_random_number do |i, x|
  conn.exec("INSERT INTO xs (x) VALUES (#{x});")
end
conn.exec("COMMIT;")
conn.exec("SELECT MEDIAN(x) FROM xs;").each do |row|
  median = row["median"].to_f
  median_err = (median - actual_median) / actual_median
  to_console "PostgreSQL estimate of #{median} (#{sprintf("%+0.4f", median_err * 100)}% error) median."
end

IO.popen("lein run 2>&1", "w+") do |io|
  sk_header = io.gets
  sk_header =~ />>> SKREAM (\d+)/
  io_pid = $1.to_i
  to_console "Running Skream process as #{io_pid} PID."
  io.puts "(track-default *sk*)"

  sk_proc = Proc.new do |sk, i, x|
    sk =~ /:median ([^,]+),/
    median = $1.to_f
    median_err = (median - actual_median) / actual_median
    mem = get_memory_usage(io_pid)
    to_console "After #{i+1} numbers, using #{sprintf("%0.2f", mem)} mB of memory, Skream estimate of #{median} (#{sprintf("%+0.4f", median_err * 100)}% error) median."
  end

  sk, i, x = nil, nil, nil
  each_random_number do |sub_i, sub_x|
    i, x = sub_i, sub_x
    io.puts x
    sk = io.gets
    sk_proc.call(sk, i, x) if ((i+1) % 2_500).zero?
  end
  sk_proc.call(sk, i, x)
end

