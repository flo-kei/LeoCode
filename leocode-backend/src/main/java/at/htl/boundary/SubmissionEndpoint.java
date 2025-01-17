package at.htl.boundary;

import at.htl.control.GitController;
import at.htl.dto.SubmissionDTO;
import at.htl.entity.*;
import at.htl.kafka.SubmissionProducer;
import at.htl.repository.ExampleRepository;
import at.htl.repository.LeocodeFileRepository;
import at.htl.repository.SubmissionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.groups.MultiSubscribe;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.jboss.logging.Logger;
import org.jboss.resteasy.annotations.SseElementType;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;

import javax.inject.Inject;
import javax.transaction.NotSupportedException;
import javax.transaction.SystemException;
import javax.transaction.Transactional;
import javax.transaction.UserTransaction;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseEventSink;
import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Path("/submission")
public class SubmissionEndpoint {

    @Inject
    @Channel("submission-result")
    Multi<Submission> results;

    @Inject
    Logger log;

    @Inject
    ExampleRepository exampleRepository;

    @Inject
    LeocodeFileRepository leocodeFileRepository;

    @Inject
    GitController gitController;

    @Inject
    SubmissionRepository submissionRepository;

    @Inject
    SubmissionProducer submissionProducer;

    @Inject
    UserTransaction transaction;


    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("test")
    public String test(){
        gitController.checkoutFolder(new File(""), new Example());
        return "dlskafj";
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response createSubmission(MultipartFormDataInput input){

        //gitController.checkoutFolder(new File("../test/"), Example.findById(39L));

        Response res;
        List<LeocodeFile> files = new LinkedList<>();
        Map<String, List<InputPart>> uploadForm = input.getFormDataMap();

        try {
            String username = uploadForm.get("username").get(0).getBodyAsString();
            String exampleId = uploadForm.get("example").get(0).getBodyAsString();
            List<InputPart> codeFiles = uploadForm.get("code");

            Example example = exampleRepository.findById(Long.parseLong(exampleId));
            if(username.isEmpty() || example == null || codeFiles.isEmpty()) {
                log.error("Username or exampleId or codeFiles is empty");
                return Response.ok("Something went wrong!").build();
            }


            //

            // Getting files from database into files list
            //search for files in db
            //files.addAll(leocodeFileRepository.getFilesRequiredForTesting(example));
            //add code from student
            //files.addAll(leocodeFileRepository.createFilesFromInputParts("code", codeFiles, username, example));

            Submission submission = new Submission();
            submission.author = username;
            submission.example = example;
            submissionRepository.persist(submission);

            submission.pathToProject = submissionRepository.copyFilesToTestDir(submission, codeFiles);
            //submission.pathToProject = submissionRepository.createSubmissionZip(submission, codeFiles);

            if(submission.pathToProject == null) {
                return Response.serverError().build();
            }

            submissionProducer.sendSubmission(submission);
            submission.setStatus(SubmissionStatus.SUBMITTED);

            log.info("createSubmission(" + submission + ")");
            log.info("Running Tests");

            return Response.ok(submission.id.toString()).build();
        } catch (Exception e) {
            e.printStackTrace();

        }

        return Response.ok("Something went wrong!").build();
    }

    @GET
    @Path("/history/{username}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listFinishedSubmissions(@PathParam("username") String username){
        List<Submission> submissions = submissionRepository.getFinishedByUsername(username);
        return Response.ok(submissionRepository.createSubmissionDTOs(submissions)).build();
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @SseElementType("text/plain")
    @Transactional()
    public void stream(@Context Sse sse, @Context SseEventSink sseEventSink, @PathParam("id") Long id) {
        Submission currentSubmission = submissionRepository.findById(id);
        boolean canSubscribe = false;

        ObjectMapper mapper = new ObjectMapper();

        // When someone refreshes or connects later on send them the current status
        if (currentSubmission != null) {
            String jsonInString = "Result mapping failed";
            try {
                jsonInString = mapper.writeValueAsString(currentSubmission.submissionResult);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

            LocalDateTime time = currentSubmission.lastTimeChanged;
            if (time == null) {
                time = LocalDateTime.now();
            }
            String res = String.format("%tT Uhr: %s;%s",
                    time.atZone(ZoneId.of( "Europe/Paris" )),
                    currentSubmission.getStatus().toString(),
                    jsonInString);

            sseEventSink.send(sse.newEvent(res));
            // anything other than SUBMITTED is complete
            canSubscribe = currentSubmission.getStatus() == SubmissionStatus.SUBMITTED;
        }

        // Only allow sse if the submition is not complete
        if (canSubscribe) {
            MultiSubscribe<Submission> subscribe = results.subscribe();

            subscribe.with(submission -> {
                if (id.equals(submission.id)) {

                    String jsonInString;
                    try {
                        jsonInString = mapper.writeValueAsString(submission.submissionResult);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }

                    LocalDateTime time = currentSubmission.lastTimeChanged;
                    if (time == null) {
                        time = LocalDateTime.now();
                    }
                    String res = String.format("%tT Uhr: %s;%s",
                            time.atZone(ZoneId.of( "Europe/Paris" )),
                            submission.getStatus().toString(),
                            jsonInString);
                            //submission.submissionResult);

                    sseEventSink.send(sse.newEvent(res));

                    if (submission.getStatus() != SubmissionStatus.SUBMITTED) {
                        sseEventSink.close();

                        //TODO: check if this is needed:
                        /*List<String> resultList = List.of(submission.result.split("\n"));
                        submission.result = resultList.get(resultList.size()-1);*/
                        log.info(submission.submissionResult);
                        log.info(submission);
                    }
                }
            });
        }
    }
}
