package io.camunda.migrator.history;

import java.util.List;

public interface IdKeyMapper {

  String findLatestIdByType(String type);

  Long findKeyById(String id);

  void insert(IdKeyDbModel idKeyDbModel);

  List<String> findProcessInstanceIds(int limit, int offset);

  void updateKeyById(IdKeyDbModel idKeyDbModel);

}
