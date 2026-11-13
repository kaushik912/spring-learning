-- one database per service, same Postgres instance. Tables are created by each service's Flyway V1__init.sql
create database orders;
create database inventory;
create database payment;
create database flowtracker;
