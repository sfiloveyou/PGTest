package com.sf.pg;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
 import com.alibaba.druid.sql.dialect.postgresql.visitor.PGSchemaStatVisitor;
 import com.alibaba.druid.stat.TableStat.*;
 import com.alibaba.druid.stat.*;
 import com.alibaba.druid.util.JdbcConstants;
  
 
 public class testparse {
  
     public static void main(String[] args) {
  
         String sql= ""
                 + "insert into tar select * from boss_table bo, ("
                     + "select a.f1, ff from emp_table a "
                     + "inner join log_table b "
                     + "on a.f2 = b.f3"
                     + ") f "
                     + "where bo.f4 = f.f5 "
                     + "group by bo.f6 , f.f7 having count(bo.f8) > 0 "
                     + "order by bo.f9, f.f10;"
                     + "select func(f) from test1; "
                     + "";
         String dbType = JdbcConstants.POSTGRESQL;
  
         //��ʽ�����
         String result = SQLUtils.format(sql, dbType);
         System.out.println(result); // ȱʡ��д��ʽ
         List<SQLStatement> stmtList = SQLUtils.parseStatements(sql, dbType);
  
         //�������Ķ������ĸ���
         System.out.println("size is:" + stmtList.size());
         for (int i = 0; i < stmtList.size(); i++) {
  
             SQLStatement stmt = stmtList.get(i);
             
             PGSchemaStatVisitor visitor = new PGSchemaStatVisitor();
             stmt.accept(visitor);
             Map<String, String> aliasmap = visitor.getAliasMap();
             for (Iterator iterator = aliasmap.keySet().iterator(); iterator.hasNext();) {
                 String key = iterator.next().toString();
                 System.out.println("[ALIAS]" + key + " - " + aliasmap.get(key));
             }
             Set<Column> groupby_col = visitor.getGroupByColumns();
             //
             for (Iterator iterator = groupby_col.iterator(); iterator.hasNext();) {
                 Column column = (Column) iterator.next();
                 System.out.println("[GROUP]" + column.toString());
             }
             //��ȡ������
             System.out.println("table names:");
             Map<Name, TableStat> tabmap = visitor.getTables();
             for (Iterator iterator = tabmap.keySet().iterator(); iterator.hasNext();) {
                 Name name = (Name) iterator.next();
                 System.out.println(name.toString() + " - " + tabmap.get(name).toString());
             }
             //System.out.println("Tables : " + visitor.getCurrentTable());
             //��ȡ������������,�����ڱ�����
             System.out.println("Manipulation : " + visitor.getTables());
             //��ȡ�ֶ�����
             System.out.println("fields : " + visitor.getColumns());
         }
  
     }
  
 }