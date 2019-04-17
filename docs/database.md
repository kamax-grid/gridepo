# Database
## PostgreSQL
### Setup
On debian, after having installed the PostgreSQL server:
```bash
su - postgres
createuser --pwprompt gridepo
psql
```
At the SQL prompt:
```sql
CREATE DATABASE "gridepo"
 ENCODING 'UTF8'
 LC_COLLATE='C'
 LC_CTYPE='C'
 template=template0
 OWNER gridepo;
```
And quit the prompt with:
```
\q
```

In your `gridepo.yaml` config file (if needed, so your install instruction), assuming a local DB:
```yaml
storage:
  database:
    connection: '//localhost/gridepo?user=gridepo&password=gridepo'
```
Adapt the password with what was provided to the `createuser` command
