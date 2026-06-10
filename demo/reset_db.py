"""Drop and recreate pulse_db using psycopg2."""
import psycopg2

conn = psycopg2.connect(
    host='localhost',
    port=5432,
    user='postgres',
    password='***',
    dbname='postgres'
)
conn.autocommit = True
cur = conn.cursor()

# Kill connections to pulse_db
cur.execute("""
    SELECT pg_terminate_backend(pid)
    FROM pg_stat_activity
    WHERE datname = 'pulse_db' AND pid <> pg_backend_pid()
""")
print("Connections terminated")

# Drop and recreate
cur.execute("DROP DATABASE IF EXISTS pulse_db")
print("Database dropped")
cur.execute("CREATE DATABASE pulse_db")
print("Database created")

cur.close()
conn.close()
print("Done")