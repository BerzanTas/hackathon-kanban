package com.berzantas.kanban.user;

import com.berzantas.kanban.user.dto.UserSummary;
import org.mapstruct.Mapper;

/** Maps a user entity to the compact summary embedded in ticket and comment responses. */
@Mapper(componentModel = "spring")
public interface UserMapper {

    UserSummary toSummary(User user);
}
