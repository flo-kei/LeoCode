package at.htl.boundary;

import at.htl.dto.RepositoryDTO;
import at.htl.entity.Repository;
import at.htl.entity.Teacher;
import at.htl.repository.TeacherRepository;
import org.jboss.logging.Logger;

import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/teacher")
public class TeacherEndpoint {

    @Inject
    Logger log;

    @Inject
    TeacherRepository teacherRepository;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public void newTeacher(Teacher teacher){
        teacher.persist();
    }

    @POST
    @Path("/newRepository")
    @Consumes(MediaType.APPLICATION_JSON)
    public void newRepository(RepositoryDTO repository){

        log.info(repository);

        teacherRepository.insertRepository(repository);
    }

    @GET
    @Path("/allTeacher")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Teacher> getAllTeacher(){
        return Teacher.listAll();
    }

    @GET
    @Path("/allRepository")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Repository> getAllRepository(){
        return Repository.listAll();
    }


}