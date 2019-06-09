# Database
## PostgreSQL
### Setup
On Debian, after having installed the PostgreSQL server:
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
    type: 'postgresql'
    connection: '//localhost/gridepo?user=gridepo&password=gridepo'
```
Change the password to what was provided to the `createuser` command
