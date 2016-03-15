# Anormous
A simple to use ORM Implementation for Android

## How to use :

#### Executing CRUD operations

#### Creation Anormous session for communicating with the database

```java
  AnormousSession session = AnormousSession.getInstance(new DBHelper(this.getApplicationContext()));
```

##### Insert operation
```java
  session.insert(new Employee("test1", "test name 1", 10, 1)); session.insert(new Employee("test2", "test name 2", 20, 2));
```
Anormous would automatically create the table for the Employee class (on the first insert command) based on either one of 1. the annotation 1. the class name It would skip the creation if the table already exists (We'd want to come up with a synching mechanism in the future that adds new columns for new properties in the bean as well)

##### Select operation
```java
  List result = (List) session.select(Employee.class);

  result = (List) session.select(Employee.class, "Id = ?", new String[] { "test1" });
  
```
There are around 12 overloads (versions) of the select method so that you can query items based on your needs.

##### Update operation
```java
  session.update(new Employee("test2", "test name 200", 200, 20));

  result = (List) session.select(Employee.class, "Id = ?", new String[] { "test2" }); // Checking the result
```
Anormous would update all records that match this record's id. It does not currently allow updating beans without an id field.

##### Delete operation
```java
  session.delete(new Employee("test1", "test name 1", 10, 1));

  result = (List) session.select(Employee.class); // Checking the result
```

Anormous would delete all records that match this record's id. It does not currently allow deleting beans without an id field. This last select is the select all operation.

##### Employee Class:
```java
import com.anormous.annotation.Column;
import com.anormous.annotation.IdentityColumn;
import com.anormous.annotation.Table;

@Table("employee")
public class Employee
{
  private String id;
  private String name;
  private Integer age;
  private Integer companyId;
  
  public Employee()
  {
    super();
  }
  
  public Employee(String id, String name, Integer age, Integer companyId)
  {
    super();
    this.id = id;
    this.name = name;
    this.age = age;
    this.companyId = companyId;
  }
  
  @IdentityColumn(value = "id", enforce = false) // setting enforce = true would make anormous to enforce primary key constraint as well
  public String getId()
  {
    return id;
  }
  
  public void setId(String id)
  {
    this.id = id;
  }
  
  @Column("name")
  public String getName()
  {
    return name;
  }
  
  public void setName(String name)
  {
    this.name = name;
  }
  
  @Column("age")
  public Integer getAge()
  {
    return age;
  }
  
  public void setAge(Integer age)
  {
    this.age = age;
  }
  
  @Column("company_id")
  public Integer getCompanyId()
  {
    return companyId;
  }
  
  public void setCompanyId(Integer companyId)
  {
    this.companyId = companyId;
  }
}
```

##### DBHelper class
```java
import com.anormous.helper.DefaultDBHelper;
import android.content.Context;

public class DBHelper extends DefaultDBHelper
{
  private static String dbName = "anormous_test.db";
  private static int dbVersion = 1;
  
  public DBHelper(Context context)
  {
    super(context, dbName, dbVersion);
  }
}

```
