# Quasar Avalanche Plugins [![Discord](https://img.shields.io/discord/373302030460125185.svg?logo=discord)](https://discord.gg/pSSqJrr)

## Datasource Configuration

JSON configuration required for constructing an Avalanche datasource.

```
{
  "connection": <connection-configuration>
}
```

* `connection`: A [connection configuration](#connection-configuration) object

## Connection Configuration

JSON configurating describing how to connect to an Avalanche cluster.

```
{
  "jdbcUrl": String
  [, "maxConcurrency": Number]
  [, "maxLifetimeSecs": Number]
}
```

* `jdbcUrl`: an Avalanche [JDBC URL](https://docs.actian.com/avalanche/index.html#page/Connectivity%2FDriverManager.getConnection()_Method--Establish.htm%23), optionally including any of the following [driver properties](https://docs.actian.com/avalanche/index.html#page/Connectivity%2FJDBC_Driver_Properties.htm%23)
  * `user`/`UID`
  * `password`/`PWD`
  * `role`/`ROLE`
  * `group`/`GRP`
  * `dbms_user`/`DBUSR`
  * `dbms_password`/`DBPWD`
  * `compression`/`COMPRESS`
  * `vnode_usage`/`VNODE`
  * `encryption`/`ENCRYPT`
  * `char_encode`/`ENCODE`
* `maxConcurrency` (optional): the maximum number of simultaneous connections to the database (default: 8)
* `maxLifetimeSecs` (optional): the maximum lifetime, in seconds, of idle connections. If your database or infrastructure imposes any limit on idle connections, make sure to set this value to at most a few seconds less than the limit (default: 210 seconds)
