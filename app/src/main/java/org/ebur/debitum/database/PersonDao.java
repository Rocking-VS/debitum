package org.ebur.debitum.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;

@Dao
public interface PersonDao {

    @Query("SELECT * FROM person order by name")
    LiveData<List<Person>> getAllPersons();

    @Query("SELECT * FROM person order by name")
    List<Person> getAllPersonsNonLive();

    @Query("select id_person from person where name = :name limit 1")
    int getPersonId(String name);

    @Query("select * from person where id_person = :id limit 1")
    Person getPersonById(int id);

    @Transaction
    @Query("select exists (select 1 from person where name=:name limit 1)")
    Boolean exists(String name);

    @Insert
    void insert(Person... persons);

    @Update
    void update(Person person);

    @Delete
    void delete(Person person);

    @Query("delete from person")
    void deleteAll();
}
