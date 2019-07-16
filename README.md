# snello-api
snello backend api

**API SERVICE**

- terminare le api base (CRUD) [X] 
- query di ricerca stile STRAPI [X]  
- gestire i query params e fare le query dinamicamente (usando MVEL) ELIMINATO [X]
- gestire allegati e relazioni con altre tabelle [X] 


**BACKEND - SERVER SIDE**
- terminare la persistenza dei METADATI:
  - Condition  [X]
  - FieldDefinition  [X]
  - Metadata  [X]
- aggiungere eventi per la gestione delle variazioni dei metadati (per la mappa dei metadati in RAM nel service)  [X]
- creare un controller che invia le liste dei nomi (es selectQuery o campi di input)  [X]


**GESTIONE METADATI**
- gestione dei modelli di contenuti (stile STRAPI..)
- vedi: nz.fiore.cms.util.ParamUtils:

- static final String EQU = "="; 
- static final String NE = "_ne"; 
- static final String _NE = "!="; 
- static final String LT = "_lt"; 
- static final String _LT = "<"; 
- static final String GT = "_gt"; 
- static final String _GT = ">"; 
- static final String LTE = "_lte"; 
- static final String _LTE = "<="; 
- static final String GTE = "_gte"; 
- static final String _GTE = ">="; 
- static final String CNT = "_contains"; 
- static final String _CNT = " LIKE "; 
- static final String _LIKE = "%"; 
- static final String CONTSS = "_containss"; 
- static final String NCNT = "_ncontains"; 
- static final String _NCNT = " NOT LIKE "; 
- static final String SPACE = " "; 
   
    // _limit=2 _start=1 _sort=page_title:desc 
- static final String _LIMIT = "_limit"; 
- static final String _START = "_start"; 
- static final String _SORT = "_sort"; 
- static final String _SELECT_FIELDS = "select_fields"; 
 
 
**GESTIONE DEI DATI** 
- gestire il backend, con forms dinamici ANG7 [X] 
- gestire i validatori 
- gestire la divisione dei FIELD divisi per GRUPPI 
- gestire i wizard di inserimento MULTI PAGINA 
 
**GESTIONE DELLE QUERY 'SCIOLTE'** 
- permettere la creazione di query sulle tabelle esistenti o create tramite metadati [X] 
- es /api/queries/cities => mappa la lista delle city uniche nella tabella users 
- query_name:  users (uuid, name, surname, city, address, postalcode) 
- select_query: select city from users group by city 

**SU BACKEND ANGULAR** 
- in LIST: gestire la lista delle campi da mostrare nella lista dei dati 
[modellato usando showInList] 
- in LIST: gestire la lista delle proprieta cercabili con campi su cui cercare nel backend 
[modellato usando searchable,searchCondition,searchFieldName] 
 
**BACKEND - CLIENT SIDE** 
- gestire autenticazione tramite API REST (login+JWT tokens) 
 
 
**DATABASE LIBERO**
- gestire la generazione dinamica delle tabelle su MYSQL [X] 
```
@Requires(property = DB_TYPE, value = "mysql")
public class MysqlJdbcRepository implements JdbcRepository {}
```

- verificare come estendere POSTGRESQL  [X] 
```
@Singleton
@Requires(property = DB_TYPE, value = "postgresql")
public class PostgresqlJdbcRepository implements JdbcRepository {}
```
- verificare come estendere H2 - per snello ALL IN ONE  [X] 
```
@Singleton
@Requires(property = DB_TYPE, value = "h2")
public class H2JdbcRepository implements JdbcRepository {
```


RUN IN DEBUG MODE
docker-compose -f docker/docker-compose-mysq.yml up
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:8787 -jar target/matcms-0.1.jar
ng serve

first login:
username: admin
password: admin


** RUN API locally **
```
docker-compose -f docker-compose-mysql.yml up
mvn compile exec:java
```

**RUN TESTS**
```
docker-compose -f docker-compose-mysql.yml up
mvn test
```
