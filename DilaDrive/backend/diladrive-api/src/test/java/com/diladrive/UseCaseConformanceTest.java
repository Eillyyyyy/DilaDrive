package com.diladrive;

import com.diladrive.service.RoadRoutingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties={"booking.push-enabled=false", "logging.level.root=OFF",
    "logging.level.org.springframework=OFF", "logging.level.com.diladrive=OFF", "spring.jpa.show-sql=false", "logging.level.org.springframework.jdbc.core=OFF", "logging.level.org.hibernate.SQL=OFF", "logging.level.org.hibernate.orm.jdbc.bind=OFF"})
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE, printOnlyOnFailure = false)
@EnabledIfEnvironmentVariable(named="DILADRIVE_CONFORMANCE_URL", matches=".+")
@Transactional
class UseCaseConformanceTest {
    @TempDir static Path storage;
    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) {
        String url = System.getenv("DILADRIVE_CONFORMANCE_URL");
        if (url == null || !url.matches("jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1):5432/DilaDrive\\?currentSchema=test_uc_[a-z0-9]+"))
            throw new IllegalStateException("A dedicated conformance schema on dev PostgreSQL is required.");
        registry.add("spring.datasource.url", () -> url);
        registry.add("app.fleet-documents.storage-dir", () -> storage.resolve("fleet").toString());
        registry.add("app.driver-data.master-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
        registry.add("app.payment-proofs.storage-dir", () -> storage.resolve("proofs").toString());
        registry.add("app.verification-photos.storage-dir", () -> storage.resolve("photos").toString());
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager persistence;
    @Autowired ObjectMapper json;
    @Autowired com.diladrive.service.UpcomingTripReminderScheduler reminders;
    @MockBean RoadRoutingService routing;
    private final String password = "Aa1!" + UUID.randomUUID();
    MockHttpSession admin, normal, company, pending, driver, otherDriver;
    long customer, corporate, driverId, otherDriverId, vehicleId, otherVehicleId, categoryId;
    LocalDateTime start;
    byte[] png;

    @BeforeEach void seed() throws Exception {
        assertEquals(System.getenv("DILADRIVE_CONFORMANCE_URL").split("=")[1], db.queryForObject("select current_schema()",String.class));
        String hash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4).encode(password);
        db.update("insert into admin(admin_name,admin_email,admin_password,admin_role) values('TEST- Admin','test-admin@example.invalid',?,'SUPER_ADMIN')", hash);
        customer = seedCustomer("normal", "NORMAL", "ACTIVE", hash);
        corporate = seedCustomer("company", "COMPANY", "ACTIVE", hash);
        seedCustomer("pending", "NORMAL", "PENDING", hash);
        categoryId = id("select vehicle_category_id from vehicle_category where category_key='SEDAN'");
        driverId = seedDriver("one", hash);
        otherDriverId = seedDriver("two", hash);
        vehicleId = seedVehicle(driverId,"TEST-ONE");
        otherVehicleId = seedVehicle(otherDriverId,"TEST-TWO");
        admin = login("admin", null);
        normal = login("normal","CUSTOMER");
        company = login("company","CUSTOMER");
        pending = login("pending","CUSTOMER");
        driver = login("one","DRIVER");
        otherDriver = login("two","DRIVER");
        start = LocalDateTime.now(ZoneId.of("Asia/Kuala_Lumpur")).plusDays(10).withHour(10).withMinute(0).withSecond(0).withNano(0);
        var out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(3,3,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",out);
        png = out.toByteArray();
        when(routing.route(any())).thenReturn(new RoadRoutingService.Route(25,1800,List.of()));
    }

    private long seedCustomer(String label,String type,String state,String hash) {
        return id("""
                insert into customer(cust_name,cust_email,cust_password,cust_type,cust_status,cust_phoneno,
                  cust_companyname,cust_companyaddress,cust_contactperson,company_registration_number)
                values(?,?,?,?,?,'0123456789','TEST- Company','TEST- Address','TEST- Contact','TEST-REG') returning cust_id
                ""","TEST- "+label,email(label),hash,type,state);
    }
    private long seedDriver(String label,String hash) {
        return id("insert into driver(driver_name,driver_email,driver_password,driver_status,driver_availability) values(?,?,?,'ACTIVE','AVAILABLE') returning driver_id",
                "TEST- "+label,email(label),hash);
    }
    private long seedVehicle(long driver,String plate) {
        return id("""
                insert into vehicle(driver_id,vehicle_category_id,vehicle_ownership,vehicle_model,vehicle_plateno,
                vehicle_type,vehicle_capacity,vehicle_luggagecapacity,vehicle_status,vehicle_roadtaxexpiry,vehicle_serviceexpiry)
                values(?,?,'DRIVER_OWNED','TEST- Sedan',?,'Sedan',3,6,'AVAILABLE','2099-01-01','2099-01-01') returning vehicle_id
                """,driver,categoryId,plate);
    }
    private static String email(String label) { return "test-"+label+"@example.invalid"; }
    private MockHttpSession login(String label,String role) throws Exception {
        var data = json.createObjectNode().put("email",email(label)).put("password",password);
        if (role != null) data.put("role",role);
        var result = mvc.perform(body(post(role==null ? "/api/auth/login" : "/api/auth/mobile/login"),data)).andReturn();
        assertEquals(200,result.getResponse().getStatus(),"fixture login status");
        assertInstanceOf(MockHttpSession.class,result.getRequest().getSession(false));
        return (MockHttpSession)result.getRequest().getSession(false);
    }
    private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request,Object data) throws Exception {
        return request.contentType("application/json").content(json.writeValueAsBytes(data));
    }
    private JsonNode expect(int status,MockHttpSession actor,MockHttpServletRequestBuilder request) throws Exception {
        if (actor != null) request.session(actor);
        var exchange=mvc.perform(request).andReturn();
        var result=exchange.getResponse();
        assertEquals(status,result.getStatus(),"HTTP status for "+exchange.getRequest().getRequestURI());
        byte[] bytes=result.getContentAsByteArray();
        return bytes.length==0 || result.getContentType()==null || !result.getContentType().contains("json")
            ? json.nullNode() : json.readTree(bytes);
    }
    private long id(String sql,Object... args) { return db.queryForObject(sql,Long.class,args); }
    private MockMultipartFile file(String part) { return new MockMultipartFile(part,"TEST-fixture.png","image/png",png); }

    private long fixtureBooking(long owner,String state,LocalDateTime when) {
        return id("""
                insert into booking(cust_id,booking_status,booking_type,booking_triptype,booking_pickupaddress,
                  booking_destinationaddress,booking_tripdate,booking_pickuptime,booking_passengercount,booking_luggagecount,booking_tripfare)
                values(?,?,'Sedan','NORMAL_TRANSFER','TEST- Pickup','TEST- Dropoff',?,?,2,0,50) returning booking_id
                """,owner,state,when.toLocalDate(),when.toLocalTime());
    }
    private Map<String,Object> trip(LocalDateTime date,int seats,int large) {
        return new LinkedHashMap<>(Map.ofEntries(Map.entry("pickupAddress","TEST- Pickup"),Map.entry("destinationAddress","TEST- Dropoff"),
            Map.entry("pickupLatitude",3.0),Map.entry("pickupLongitude",101.0),Map.entry("destinationLatitude",3.1),Map.entry("destinationLongitude",101.1),
            Map.entry("scheduledAt",date.toString()),Map.entry("passengerCount",seats),Map.entry("smallCount",0),
            Map.entry("mediumCount",0),Map.entry("largeCount",large),Map.entry("tripType","NORMAL_TRANSFER")));
    }
    private Map<String,Object> approved(MockHttpSession actor, LocalDateTime date) throws Exception {
        var data=trip(date,2,0);
        var quote=expect(200,actor,body(post("/api/bookings/estimate"),data));
        assertTrue(quote.path("available").asBoolean());
        data.put("vehicleCategory",quote.path("recommendedVehicleType").asText());
        data.put("acceptedQuoteToken",quote.path("quoteToken").asText());
        return data;
    }
    private long createBooking(MockHttpSession actor,LocalDateTime date) throws Exception {
        return expect(201,actor,body(post("/api/bookings/customer/complete").header("Idempotency-Key","TEST-"+UUID.randomUUID()),approved(actor,date))).path("bookingId").asLong();
    }
    private Map<String,Object> pair(long booking,long person,long vehicle,LocalDateTime when) {
        return Map.of("bookingId",booking,"driverId",person,"vehicleId",vehicle,"scheduledEndAt",when.plusHours(2).toString(),"reason","TEST- reassignment");
    }
    private long confirm(long booking,long person,long vehicle,LocalDateTime when) throws Exception {
        return expect(200,admin,body(post("/api/bookings/"+booking+"/confirm-and-assign"),pair(booking,person,vehicle,when))).path("assignmentId").asLong();
    }
    private long quote(long booking) throws Exception {
        return expect(201,admin,body(post("/api/documents/quotation"),Map.of("bookingId",booking,"amount",50,"remarks","TEST- Quotation"))).path("documentId").asLong();
    }
    private long payment(MockHttpSession actor,long booking,String amount,int status) throws Exception {
        return expect(status,actor,multipart("/api/payments/bookings/"+booking).file(file("proof"))
                .param("amount",amount).param("paymentMethod","BANK_TRANSFER")).path("paymentId").asLong();
    }
    private long fixturePayment(long booking,String state) {
        return id("insert into payment(booking_id,amount,payment_method,payment_status) values(?,50,'BANK_TRANSFER',?) returning payment_id",booking,state);
    }
    private long fixtureDocument(long owner,long booking,String type,String state) {
        return id("""
                insert into documents(document_number,document_type,booking_id,cust_id,cust_name,amount,status)
                values(?,?,?,?,'TEST- Customer',50,?) returning document_id
                ""","TEST-"+UUID.randomUUID().toString().substring(0,12),type,booking,owner,state);
    }
    private long register(String label,String type,int status) throws Exception {
        var data=json.createObjectNode().put("name","TEST- "+label).put("email",email(label)).put("phone","0123456777")
            .put("password",password).put("confirmPassword",password).put("type",type);
        if(type.equals("COMPANY")) data.put("companyName","TEST- "+label).put("companyAddress","TEST- Address")
            .put("contactPerson","TEST- Contact").put("companyRegistrationNumber","TEST-REG");
        expect(status,null,body(post("/api/auth/mobile/register"),data));
        return status==201?id("select cust_id from customer where cust_email=?",email(label)):0;
    }

    private void verify(long owner, String decision, int status, MockHttpSession actor, String reason) throws Exception {
        expect(status,actor,body(patch("/api/customers/"+owner+"/verification"),
                reason.isEmpty() ? Map.of("decision",decision) : Map.of("decision",decision,"reason",reason)));
    }
    private void review(long paymentId, MockHttpSession actor, int status) throws Exception {
        expect(status,actor,body(put("/api/payments/"+paymentId+"/review"),Map.of("decision","VERIFIED")));
    }
    private long receipt(long booking) {
        return id("select document_id from documents where booking_id=? and document_type='RECEIPT'",booking);
    }
    private void status(long assignment, String value, int expected) throws Exception {
        expect(expected,driver,body(patch("/api/assignments/"+assignment+"/status"),Map.of("status",value)));
        if(expected==200) assertEquals(1,id("select count(*) from driver_assignment where assignment_id=? and assignment_status=?",assignment,value));
    }
    private void photo(long assignment, String type, int expected, MockHttpSession actor) throws Exception {
        expect(expected,actor,multipart("/api/assignments/"+assignment+"/photos").file(file("file")).param("photoType",type));
    }
    private void complete(long assignment) throws Exception {
        assertEquals(1,id("select count(*) from driver_assignment where assignment_id=? and assignment_status='ASSIGNED'",assignment));
        status(assignment,"OTW",200);
        status(assignment,"WAITING",200);
        photo(assignment,"PICKUP",201,driver);
        status(assignment,"PICKUP",200);
        photo(assignment,"DROP",201,driver);
        status(assignment,"DROP",200);
        status(assignment,"COMPLETED",200);
    }
    private long recordPo(long quotation) throws Exception {
        return expect(201,admin,body(post("/api/documents/"+quotation+"/record-po"),
                Map.of("clientReference","TEST-PO"))).path("documentId").asLong();
    }
    private long generateInvoice(long source, String type, int status) throws Exception {
        return expect(status,admin,body(post("/api/documents/"+source+"/generate-invoice"),
                Map.of("sourceType",type))).path("documentId").asLong();
    }
    private void rejectBooking(long booking, MockHttpSession actor,int status) throws Exception {
        expect(status,actor,body(post("/api/bookings/"+booking+"/reject"),Map.of("reason","TEST- unavailable")));
    }
    private MockHttpServletRequestBuilder newDriver() throws Exception {
        var vehicle = Map.of("vehicleCategoryId",categoryId,"vehicleModel","TEST- Car","vehiclePlateno","TEST123",
                "vehicleOwnership","DRIVER_OWNED","vehicleCapacity",3,"vehicleLuggageCapacity",6,"vehicleStatus","AVAILABLE","vehicleRoadtaxexpiry","2099-01-01","vehicleServiceexpiry","2099-01-01");
        var data = Map.of("driverName","TEST- Driver","driverEmail",email("new-driver"),"driverPassword",password,
                "driverPhoneno","0123456789","driverLicenseno","TEST123","driverLicenseExpiry","2099-01-01",
                "driverType","Company Driver","vehicle",vehicle);
        return multipart("/api/drivers").file(new MockMultipartFile("driver","","application/json",json.writeValueAsBytes(data)))
                .file(file("licenseDocument")).file(file("roadTaxDocument"));
    }

    @Test void uc001CreateAccounts() throws Exception {
        var data=Map.of("name","TEST- Admin","email",email("new-admin"),"phone","0123456789","address","TEST- Address",
                "password",password,"confirmPassword",password,"role","ADMIN");
        expect(201,admin,body(post("/api/admins"),data));
        register("new-normal","NORMAL",201);
        register("new-company","COMPANY",201);
        expect(201,admin,newDriver());
        expect(403,normal,body(post("/api/admins"),data));
    }
    @Test void uc002ViewAccounts() throws Exception {
        expect(200,admin,get("/api/auth/session"));
        for (var actor:List.of(normal,company)) expect(200,actor,get("/api/customers/me"));
        expect(200,driver,get("/api/drivers/me"));
        expect(401,null,get("/api/customers/me"));
    }
    @Test void uc003UpdateAccounts() throws Exception {
        expect(200,admin,body(put("/api/auth/profile"),Map.of("name","TEST- Updated Admin","email",email("admin"))));
        expect(200,normal,body(put("/api/customers/me"),Map.of("name","TEST- Updated Customer","email",email("normal"))));
        expect(200,company,body(put("/api/customers/me"),Map.of("companyName","TEST- Updated Company","companyRegistrationNumber","TEST-REG","name","TEST- company","email",email("company"))));
        expect(200,driver,body(put("/api/drivers/me"),Map.of("name","TEST- Updated Driver","email",email("one"),"phone","0123456789")));
        expect(400,normal,body(put("/api/customers/me"),Map.of("phone","invalid")));
    }
    @Test void uc004VerifyAccounts() throws Exception {
        long approved=register("to-approve","NORMAL",201);
        long rejected=register("to-reject","COMPANY",201);
        verify(approved,"APPROVE",200,admin,"");
        verify(rejected,"REJECT",200,admin,"TEST- Missing details");
        verify(approved,"REJECT",409,admin,"TEST- Already processed");
    }
    @Test void uc005CreateVehicle() throws Exception {
        expect(201,admin,newDriver());
        assertEquals(1,id("select count(*) from vehicle where vehicle_plateno='TEST123'"));
        expect(403,normal,newDriver());
    }
    @Test void uc006ViewVehicle() throws Exception {
        expect(200,admin,get("/api/vehicles/"+vehicleId));
        expect(404,admin,get("/api/vehicles/999999"));
    }
    @Test void uc007UpdateVehicle() throws Exception {
        expect(200,admin,body(put("/api/vehicles/"+vehicleId),Map.of("vehicleModel","TEST- Updated","vehiclePlateno","TEST-ONE","vehicleStatus","AVAILABLE","vehicleCategoryId",categoryId,"vehicleOwnership","DRIVER_OWNED","vehicleRoadtaxexpiry","2099-01-01","vehicleServiceexpiry","2099-01-01")));
        expect(403,normal,body(put("/api/vehicles/"+vehicleId),Map.of("vehicleModel","TEST- Blocked")));
    }
    @Test void uc008CreateBooking() throws Exception {
        assertTrue(createBooking(normal,start)>0);
        expect(403,pending,body(post("/api/bookings/customer/complete"),approved(normal,start.plusDays(1))));
    }
    @Test void uc009ViewBooking() throws Exception {
        long booking=fixtureBooking(customer,"PENDING",start);
        expect(200,admin,get("/api/bookings"));
        expect(200,normal,get("/api/bookings/mine/"+booking));
        expect(404,company,get("/api/bookings/mine/"+booking));
    }
    @Test void uc010UpdateBooking() throws Exception {
        long booking=createBooking(normal,start);
        var payload=approved(normal,start.plusDays(1));
        expect(200,normal,body(put("/api/bookings/"+booking+"/customer/complete"),payload));
        expect(404,company,body(put("/api/bookings/"+booking+"/customer/complete"),payload));
    }
    @Test void uc011CancelBooking() throws Exception {
        long booking=fixtureBooking(customer,"PENDING",start);
        expect(404,company,post("/api/bookings/"+booking+"/cancel"));
        expect(200,normal,post("/api/bookings/"+booking+"/cancel"));
    }
    @Test void uc012VerifyBooking() throws Exception {
        long booking=createBooking(normal,start);
        assertTrue(confirm(booking,driverId,vehicleId,start)>0);
        rejectBooking(fixtureBooking(customer,"PENDING",start.plusDays(1)),admin,204);
        rejectBooking(booking,normal,403);
    }
    @Test void uc013AssignDriverVehicle() throws Exception {
        long booking=fixtureBooking(customer,"CONFIRMED",start);
        expect(201,admin,body(post("/api/assignments"),pair(booking,driverId,vehicleId,start)));
        expect(409,admin,body(post("/api/assignments"),pair(booking,otherDriverId,otherVehicleId,start)));
    }
    @Test void uc014ReassignTrip() throws Exception {
        long booking=createBooking(normal,start);
        long assignment=confirm(booking,driverId,vehicleId,start);
        expect(200,admin,body(put("/api/assignments/"+assignment+"/reassign"),pair(booking,otherDriverId,otherVehicleId,start)));
        expect(403,normal,body(put("/api/assignments/"+assignment+"/reassign"),pair(booking,driverId,vehicleId,start)));
    }
    @Test void uc015ViewAssignedTrip() throws Exception {
        long assignment=confirm(createBooking(normal,start),driverId,vehicleId,start);
        expect(200,driver,get("/api/assignments/mine/"+assignment));
        expect(404,otherDriver,get("/api/assignments/mine/"+assignment));
    }
    @Test void uc016ViewTripDetails() throws Exception {
        long booking=createBooking(normal,start);
        long assignment=confirm(booking,driverId,vehicleId,start);
        expect(200,admin,get("/api/assignments/"+assignment));
        expect(200,normal,get("/api/bookings/"+booking+"/timeline"));
        expect(404,company,get("/api/bookings/"+booking+"/timeline"));
    }
    @Test void uc017UpdateTripStatus() throws Exception {
        long assignment=confirm(createBooking(normal,start),driverId,vehicleId,start);
        status(assignment,"OTW",200);
        status(assignment,"WAITING",200);
        status(assignment,"PICKUP",409);
    }
    @Test void uc018CreateQuotation() throws Exception {
        assertTrue(quote(fixtureBooking(corporate,"PENDING",start))>0);
        long normalBooking=fixtureBooking(customer,"PENDING",start);
        var result=expect(409,admin,body(post("/api/documents/quotation"),Map.of("bookingId",normalBooking,"amount",50)));
        assertEquals("Quotations are for corporate customers only.",result.path("message").asText());
        assertEquals(0,id("select count(*) from documents where booking_id=?",normalBooking));
    }
    @Test void uc019ViewQuotation() throws Exception {
        long doc=fixtureDocument(corporate,fixtureBooking(corporate,"PENDING",start),"QUOTATION","APPROVED");
        expect(200,admin,get("/api/documents/"+doc));
        expect(200,company,get("/api/documents/customer/"+doc+"/pdf"));
        expect(403,normal,get("/api/documents/customer/"+doc+"/pdf"));
    }
    @Test void uc020UpdateQuotation() throws Exception {
        long draft=fixtureDocument(corporate,fixtureBooking(corporate,"PENDING",start),"QUOTATION","DRAFT");
        long approved=fixtureDocument(corporate,fixtureBooking(corporate,"PENDING",start),"QUOTATION","APPROVED");
        expect(200,admin,body(put("/api/documents/"+draft),Map.of("bookingId",id("select booking_id from documents where document_id=?",draft),"amount",60,"remarks","TEST- Revised")));
        expect(409,admin,body(put("/api/documents/"+approved),Map.of("bookingId",id("select booking_id from documents where document_id=?",approved),"amount",60,"remarks","TEST- Locked")));
    }
    @Test void uc021RecordPo() throws Exception {
        long doc=fixtureDocument(corporate,fixtureBooking(corporate,"PENDING",start),"QUOTATION","APPROVED");
        assertTrue(recordPo(doc)>0);
        expect(410,admin,post("/api/documents/"+doc+"/generate-po"));
    }
    @Test void uc022GenerateInvoice() throws Exception {
        long booking=fixtureBooking(corporate,"COMPLETED",start);
        assertTrue(generateInvoice(booking,"BOOKING",201)>0);
        generateInvoice(booking,"BOOKING",409);
    }
    @Test void uc023ViewInvoice() throws Exception {
        long doc=fixtureDocument(corporate,fixtureBooking(corporate,"COMPLETED",start),"INVOICE","UNPAID");
        expect(200,admin,get("/api/documents/"+doc+"/pdf"));
        expect(200,company,get("/api/documents/customer/"+doc+"/pdf"));
        expect(403,normal,get("/api/documents/customer/"+doc+"/pdf"));
    }
    @Test void uc024ViewNotifications() throws Exception {
        expect(200,admin,get("/api/notifications/inbox"));
        expect(200,normal,get("/api/notifications/customer/mine"));
        expect(200,company,get("/api/notifications/customer/mine"));
        expect(200,driver,get("/api/notifications/driver/"+driverId));
        expect(403,otherDriver,get("/api/notifications/driver/"+driverId));
    }
    @Test void uc025UploadPaymentProof() throws Exception {
        assertTrue(payment(normal,fixtureBooking(customer,"COMPLETED",start),"50",201)>0);
        payment(normal,fixtureBooking(customer,"PENDING",start),"50",409);
    }
    @Test void uc026VerifyPayment() throws Exception {
        long pay=fixturePayment(fixtureBooking(customer,"COMPLETED",start),"PENDING_VERIFICATION");
        review(pay,admin,200);
        review(pay,admin,409);
    }
    @Test void uc027ViewPaymentHistory() throws Exception {
        long pay=payment(normal,fixtureBooking(customer,"COMPLETED",start),"50",201);
        expect(200,normal,get("/api/payments/mine"));
        expect(200,admin,get("/api/payments"));
        expect(200,normal,get("/api/payments/"+pay+"/proof"));
        expect(404,company,get("/api/payments/"+pay+"/proof"));
    }
    @Test void uc028GenerateReceipt() throws Exception {
        long booking=fixtureBooking(customer,"COMPLETED",start);
        long pay=fixturePayment(booking,"PENDING_VERIFICATION");
        assertEquals(0,id("select count(*) from documents where booking_id=? and document_type='RECEIPT'",booking));
        review(pay,admin,200);
        assertTrue(receipt(booking)>0);
        expect(410,admin,post("/api/documents/"+receipt(booking)+"/generate-receipt"));
    }
    @Test void uc029ViewReceipt() throws Exception {
        for (var actor:List.of(normal,company)) {
            long booking=fixtureBooking(actor==normal?customer:corporate,"COMPLETED",start);
            review(fixturePayment(booking,"PENDING_VERIFICATION"),admin,200);
            long doc=receipt(booking);
            expect(200,admin,get("/api/documents/"+doc+"/pdf"));
            expect(200,actor,get("/api/documents/customer/receipts"));
            expect(200,actor,get("/api/documents/customer/receipts/"+doc+"/pdf"));
            expect(404,actor==normal?company:normal,get("/api/documents/customer/receipts/"+doc+"/pdf"));
        }
    }
    @Test void uc030RecordCash() throws Exception {
        long booking=createBooking(normal,start);
        complete(confirm(booking,driverId,vehicleId,start));
        expect(201,driver,body(post("/api/payments/bookings/"+booking+"/cash"),Map.of("amount",50)));
        expect(201,admin,body(post("/api/payments/bookings/"+fixtureBooking(customer,"COMPLETED",start.plusDays(1))+"/cash"),Map.of("amount",50)));
        expect(403,normal,body(post("/api/payments/bookings/"+booking+"/cash"),Map.of("amount",1)));
    }

    @Test void supportAvailabilityAndRecommendation() throws Exception {
        db.update("update vehicle set vehicle_type='MPV',vehicle_capacity=5,vehicle_luggagecapacity=12, vehicle_category_id=(select vehicle_category_id from vehicle_category where category_key='MPV') where vehicle_id=?",otherVehicleId);
        persistence.clear();
        var fitting=expect(200,normal,body(post("/api/bookings/availability"),trip(start,1,3)));
        assertTrue(fitting.path("available").asBoolean(),"A nine-unit load should fit the MPV.");
        assertEquals("MPV",fitting.path("recommendedVehicleType").asText());
        assertFalse(expect(200,normal,body(post("/api/bookings/availability"),trip(start,1,5))).path("available").asBoolean());
        var estimate=expect(200,normal,body(post("/api/bookings/availability"),trip(start,1,4)));
        assertTrue(estimate.path("available").asBoolean(),"One passenger and four large bags should fit a 12-unit MPV; reported "+estimate.path("availabilityStatus").asText());
        assertEquals("MPV",estimate.path("recommendedVehicleType").asText());
    }

    @Test void supportFareAndCharges() throws Exception {
        var estimate=expect(200,normal,body(post("/api/bookings/estimate"),trip(start,2,0)));
        assertEquals(25,estimate.path("estimatedDistanceKm").asInt());
        long booking=fixtureBooking(customer,"COMPLETED",start);
        String path="/api/admin/bookings/"+booking+"/charges/preview";
        var charges=expect(200,admin,body(post(path),Map.of("extraHours",1,"toll",4,"parking",3,"reason","TEST- Charges")));
        assertEquals(0,charges.path("finalFare").decimalValue().compareTo(charges.path("hourlyAmount").decimalValue().add(java.math.BigDecimal.valueOf(57))));
        expect(400,admin,body(post(path),Map.of("extraHours",-1,"toll",4,"parking",3,"reason","TEST- Charges")));
    }

    @Test void supportPhotosAndIncidents() throws Exception {
        long assignment=confirm(createBooking(normal,start),driverId,vehicleId,start);
        status(assignment,"OTW",200);
        status(assignment,"WAITING",200);
        photo(assignment,"PICKUP",404,otherDriver);
        photo(assignment,"PICKUP",201,driver);
        expect(200,admin,get("/api/assignments/"+assignment+"/photos"));
        incident(assignment);
        expect(200,admin,get("/api/assignments/incidents"));
        expect(404,otherDriver,body(post("/api/assignments/"+assignment+"/incidents"),
                Map.of("category","DELAY","description","TEST- Unauthorized")));
    }

    private void incident(long assignment) throws Exception {
        expect(201,driver,body(post("/api/assignments/"+assignment+"/incidents"),
                Map.of("category","DELAY","description","TEST- Delay")));
    }

    @Test void supportCalendarAndSendNotification() throws Exception {
        assertTrue(confirm(createBooking(normal,start),driverId,vehicleId,start)>0);
        expect(200,admin,get("/api/assignments/schedule").param("driverId",Long.toString(driverId)).param("date",start.toLocalDate().toString()));
        expect(400,admin,get("/api/assignments/schedule"));
        expect(201,admin,body(post("/api/notifications"),
                Map.of("driverId",driverId,"title","TEST- Message","message","TEST- Schedule","notificationType","GENERAL")));
        expect(200,driver,get("/api/notifications/driver/"+driverId));
        driverNotice(driverId,"TEST- Message","GENERAL");
        expect(404,admin,body(post("/api/notifications"),
                Map.of("driverId",999999,"title","TEST- Message","message","TEST- Invalid recipient","notificationType","GENERAL")));
    }

    @Test void scenario1Corporate() throws Exception {
        long owner=register("corporate-scenario","COMPANY",201);
        verify(owner,"APPROVE",200,admin,"");
        var actor=login("corporate-scenario","CUSTOMER");
        long booking=createBooking(actor,start);
        long quotation=quote(booking);
        expect(200,admin,put("/api/documents/"+quotation+"/approve"));
        long po=recordPo(quotation);
        complete(confirm(booking,driverId,vehicleId,start));
        long invoice=generateInvoice(po,"PURCHASE_ORDER",201);
        review(payment(actor,booking,"50",201),admin,200);
        expect(200,actor,get("/api/documents/customer/receipts/"+receipt(booking)+"/pdf"));
        expect(200,actor,get("/api/documents/customer/"+quotation+"/pdf"));
        expect(200,actor,get("/api/documents/customer/"+invoice+"/pdf"));
    }

    @Test void scenario2Normal() throws Exception {
        long owner=register("normal-scenario","NORMAL",201);
        verify(owner,"APPROVE",200,admin,"");
        var actor=login("normal-scenario","CUSTOMER");
        long booking=createBooking(actor,start);
        complete(confirm(booking,driverId,vehicleId,start));
        review(payment(actor,booking,"50",201),admin,200);
        expect(200,actor,get("/api/documents/customer/receipts/"+receipt(booking)+"/pdf"));
    }

    @Test void scenario3OverlapAndReassignment() throws Exception {
        long first=createBooking(normal,start);
        long original=confirm(first,driverId,vehicleId,start);
        long second=createBooking(company,start);
        expect(409,admin,body(post("/api/bookings/"+second+"/confirm-and-assign"),pair(second,driverId,vehicleId,start)));
        assertTrue(confirm(second,otherDriverId,otherVehicleId,start)>0);
        expect(409,admin,body(put("/api/assignments/"+original+"/reassign"),pair(first,otherDriverId,otherVehicleId,start)));
        LocalDateTime later=start.plusDays(2);
        long booking=createBooking(normal,later);
        long assignment=confirm(booking,driverId,vehicleId,later);
        long replacement=expect(200,admin,body(put("/api/assignments/"+assignment+"/reassign"),pair(booking,otherDriverId,otherVehicleId,later))).path("assignmentId").asLong();
        assertEquals(1,id("select count(*) from driver_assignment where assignment_id=? and booking_id=? and assignment_status='SUPERSEDED'",assignment,booking));
        assertEquals(1,id("select count(*) from driver_assignment where assignment_id=? and booking_id=? and previous_assignment_id=?",replacement,booking,assignment));
        expect(200,admin,get("/api/assignments/"+assignment+"/timeline"));
    }

    @Test void scenario4AccessControl() throws Exception {
        long rejected=register("rejected-scenario","NORMAL",201);
        verify(rejected,"REJECT",200,admin,"TEST- Reject");
        var rejectedActor=login("rejected-scenario","CUSTOMER");
        var candidate=approved(normal,start);
        expect(403,pending,body(post("/api/bookings/customer/complete"),candidate));
        expect(403,rejectedActor,body(post("/api/bookings/customer/complete"),candidate));
        long booking=createBooking(company,start);
        long assignment=confirm(booking,driverId,vehicleId,start);
        expect(404,normal,get("/api/bookings/mine/"+booking));
        expect(404,normal,get("/api/bookings/"+booking+"/timeline"));
        expect(404,otherDriver,get("/api/assignments/mine/"+assignment));
        long doc=fixtureDocument(corporate,booking,"QUOTATION","APPROVED");
        long owner=register("other-company","COMPANY",201);
        verify(owner,"APPROVE",200,admin,"");
        expect(404,login("other-company","CUSTOMER"),get("/api/documents/customer/"+doc+"/pdf"));
        long pay=payment(company,fixtureBooking(corporate,"COMPLETED",start.plusDays(1)),"50",201);
        expect(404,normal,get("/api/payments/"+pay+"/proof"));
    }

    @Test void scenario5Notifications() throws Exception {
        long owner=register("notify-company","COMPANY",201);
        verify(owner,"APPROVE",200,admin,"");
        var actor=login("notify-company","CUSTOMER");
        long booking=createBooking(actor,start);
        long quotation=quote(booking);
        expect(200,admin,put("/api/documents/"+quotation+"/approve"));
        long assignment=confirm(booking,driverId,vehicleId,start);
        var reassigned=expect(200,admin,body(put("/api/assignments/"+assignment+"/reassign"),pair(booking,otherDriverId,otherVehicleId,start)));
        long replacement=reassigned.path("assignmentId").asLong();
        MockHttpSession originalDriver=driver;
        driver=otherDriver;
        incident(replacement);
        complete(replacement);
        driver=originalDriver;
        long invoice=generateInvoice(booking,"BOOKING",201);
        review(payment(actor,booking,"50",201),admin,200);
        rejectBooking(fixtureBooking(owner,"PENDING",start.plusDays(1)),admin,204);
        expect(200,admin,get("/api/notifications/inbox"));
        expect(200,actor,get("/api/notifications/customer/mine"));
        expect(200,driver,get("/api/notifications/driver/"+driverId));
        expect(200,otherDriver,get("/api/notifications/driver/"+otherDriverId));
        long adminId=id("select admin_id from admin where admin_email=?",email("admin"));
        notice("ADMIN",adminId,"CUSTOMER_REGISTRATION");
        notice("CUSTOMER",owner,"ACCOUNT_VERIFICATION");
        notice("ADMIN",adminId,"BOOKING");
        notice("CUSTOMER",owner,"BOOKING_APPROVED");
        notice("CUSTOMER",owner,"BOOKING_REJECTED");
        driverNotice(driverId,"Assignment Update");
        driverNotice(driverId,"Assignment changed");
        driverNotice(otherDriverId,"Assignment Update");
        notice("ADMIN",adminId,"ASSIGNMENT_REASSIGNED");
        notice("CUSTOMER",owner,"DRIVER_ASSIGNED");
        notice("CUSTOMER",owner,"TRIP_STATUS");
        notice("ADMIN",adminId,"TRIP_STATUS");
        notice("ADMIN",adminId,"TRIP_INCIDENT");
        notice("CUSTOMER",owner,"QUOTATION_APPROVED");
        documentNotice(owner,invoice,"INVOICE");
        assertReceiptNotice(owner,booking,actor,normal);

    }

    private void notice(String role,long recipient,String event) {
        assertTrue(id("select count(*) from notification where recipient_role=? and recipient_id=? and notification_type=?",
                role,recipient,event)>0,"Missing "+role+" event "+event);
    }

    private void driverNotice(long driverId,String title) {
        driverNotice(driverId,title,"ASSIGNMENT");
    }

    private void driverNotice(long driverId,String title,String event) {
        assertTrue(id("select count(*) from notification where driver_id=? and notification_type=? and notification_title=?",
                driverId,event,title)>0,"Missing driver event "+title);
    }

    private void documentNotice(long owner,long document,String type) {
        assertTrue(id("""
                select count(*) from notification n join documents d on d.document_id=?
                where n.recipient_role='CUSTOMER' and n.recipient_id=? and n.notification_type='DOCUMENT'
                    and position(d.document_number in n.notification_message)>0
                """,document,owner)>0,"Missing customer document event "+type);
    }


    @Test void receiptNotificationIsVisibleOnlyToTheBookingOwner() throws Exception {
        for (var actor : List.of(normal, company)) {
            long owner = actor == normal ? customer : corporate;
            long booking = fixtureBooking(owner, "COMPLETED", start);
            long pay = fixturePayment(booking, "PENDING_VERIFICATION");
            assertTrue(receiptNotices(owner, booking).isEmpty());
            review(pay, admin, 200);
            review(pay, admin, 409);
            assertTrue(receipt(booking) > 0);
            assertReceiptNotice(owner, booking, actor, actor == normal ? company : normal);
        }
    }

    private List<Long> receiptNotices(long owner, long booking) {
        return db.queryForList("""
                select notification_id from notification where recipient_role='CUSTOMER' and recipient_id=?
                    and notification_type='RECEIPT_AVAILABLE' and position(? in notification_message)>0
                """, Long.class, owner, "BK-%06d".formatted(booking));
    }

    private void assertReceiptNotice(long owner, long booking, MockHttpSession actor, MockHttpSession other) throws Exception {
        var notices = receiptNotices(owner, booking);
        assertEquals(1, notices.size(), "Exactly one receipt notification for the owner and booking");
        long noticeId = notices.get(0);
        assertTrue(hasNotification(expect(200, actor, get("/api/notifications/customer/mine")), noticeId));
        assertFalse(hasNotification(expect(200, other, get("/api/notifications/customer/mine")), noticeId));
        expect(404, other, patch("/api/notifications/" + noticeId + "/read"));
        expect(200, actor, patch("/api/notifications/" + noticeId + "/read"));
    }

    private boolean hasNotification(JsonNode inbox, long noticeId) {
        assertTrue(inbox.isArray(), "Expected the customer inbox array");
        for (var item : inbox) {
            if (item.path("notificationId").asLong() == noticeId) return true;
        }
        return false;
    }

    @Test void upcomingRemindersReachOnlyTheAssignedPairOncePerWindow() throws Exception {
        long booking = createBooking(normal, start);
        confirm(booking, driverId, vehicleId, start);
        reminders.sendUpcomingReminders(start.minusHours(24).minusSeconds(1));
        assertReminderRecipients(booking, "24H", List.of());
        reminders.sendUpcomingReminders(start.minusHours(24));
        reminders.sendUpcomingReminders(start.minusHours(24));
        var recipients = List.of("CUSTOMER:" + customer, "DRIVER:" + driverId);
        assertReminderRecipients(booking, "24H", recipients);
        assertReminderRecipients(booking, "2H", List.of());
        reminders.sendUpcomingReminders(start.minusHours(2));
        reminders.sendUpcomingReminders(start.minusHours(2));
        assertReminderRecipients(booking, "24H", recipients);
        assertReminderRecipients(booking, "2H", recipients);
    }

    private void assertReminderRecipients(long booking, String window, List<String> expected) {
        assertEquals(expected, db.queryForList("""
                select recipient_role || ':' || recipient_id from notification
                where notification_type=? and event_key like ? order by recipient_role,recipient_id
                """, String.class, "TRIP_REMINDER_" + window, "trip-reminder:" + window + ":" + booking + ":%"));
    }

    @Test void adminManagesDriverAndVehicleUnavailablePeriods() throws Exception {
        long booking = createBooking(normal, start);
        confirm(booking, driverId, vehicleId, start);
        checkUnavailability("DRIVER", driverId, booking);
        checkUnavailability("VEHICLE", vehicleId, booking);
    }

    private void checkUnavailability(String kind, long resource, long booking) throws Exception {
        String url = "/api/admin/unavailability/" + kind + "/" + resource;
        var input = Map.of("startAt", start.plusDays(1).toString(),
                "endAt", start.plusDays(1).plusHours(1).toString(), "reason", "TEST- maintenance");
        long period = expect(201, admin, body(post(url), input)).path("id").asLong();
        var listed = expect(200, admin, get(url));
        assertEquals(1, listed.size());
        assertEquals(period, listed.get(0).path("id").asLong());
        expect(204, admin, delete(url + "/" + period));
        assertTrue(expect(200, admin, get(url)).isEmpty());
        var conflict = expect(409, admin, body(post(url), Map.of(
                "startAt", start.toString(), "endAt", start.plusHours(1).toString())));
        assertEquals(1, conflict.path("bookingIds").size());
        assertEquals(booking, conflict.path("bookingIds").get(0).asLong());
        expect(403, normal, body(post(url), input));
    }

    @Test void lastActiveSuperAdminCannotBeDemotedOrDeactivated() throws Exception {
        long first = id("select admin_id from admin where admin_email=?", email("admin"));
        long second = seedAdmin("second-super", "SUPER_ADMIN");
        expect(200, admin, body(put("/api/admins/" + second), adminChange("second-super", "ADMIN", "ACTIVE")));
        expect(409, admin, body(put("/api/admins/" + first), adminChange("admin", "ADMIN", "ACTIVE")));
        expect(409, admin, body(put("/api/admins/" + first), adminChange("admin", "SUPER_ADMIN", "INACTIVE")));
        assertEquals(1, id("select count(*) from admin where admin_role='SUPER_ADMIN' and admin_status='ACTIVE'"));
    }

    @Test void deactivatedAdminCannotUseAnExistingSession() throws Exception {
        long target = seedAdmin("live-admin", "ADMIN");
        var live = login("live-admin", null);
        expect(200, live, get("/api/bookings"));
        expect(200, admin, body(put("/api/admins/" + target), adminChange("live-admin", "ADMIN", "INACTIVE")));
        persistence.flush();
        persistence.clear();
        expect(401, live, get("/api/bookings"));
    }

    private long seedAdmin(String label, String role) {
        String hash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4).encode(password);
        return id("insert into admin(admin_name,admin_email,admin_password,admin_role) values(?,?,?,?) returning admin_id",
                "TEST- " + label, email(label), hash, role);
    }

    private Map<String,String> adminChange(String label, String role, String status) {
        return Map.of("name", "TEST- " + label, "email", email(label), "phone", "0123456789",
                "address", "TEST- Address", "role", role, "status", status);
    }

    @Test void confirmedBookingCanMoveWithinItsOwnWindowAndKeepsItsAssignment() throws Exception {
        long booking = createBooking(normal, start);
        long assignment = confirm(booking, driverId, vehicleId, start);
        var pickup = start.plusMinutes(15);
        schedule(204, normal, booking, pickup);
        assertTripWindow(booking, assignment, pickup);
        assertEquals(1, id("select count(*) from driver_assignment where booking_id=?", booking));
        assertEquals(1, id("""
                select count(*) from notification where recipient_role='ADMIN'
                and entity_id=? and notification_type='BOOKING_RESCHEDULED'
                and position(? in notification_message)>0 and position(? in notification_message)>0
                """, booking, start.toString(), pickup.toString()));
        assertEquals(1, id("""
                select count(*) from notification where driver_id=? and notification_type='BOOKING_RESCHEDULED'
                and position(? in notification_message)>0 and position(? in notification_message)>0
                """, driverId, start.toString(), pickup.toString()));
        schedule(204, normal, booking, pickup.plusDays(1));
        assertTripWindow(booking, assignment, pickup.plusDays(1));
    }

    @Test void confirmedBookingRescheduleRejectsBusyPairWithoutChangingTheTrip() throws Exception {
        long booking = createBooking(normal, start);
        long assignment = confirm(booking, driverId, vehicleId, start);
        var later = start.plusDays(1);
        long competing = createBooking(company, later);
        confirm(competing, driverId, vehicleId, later);
        assertTrue(schedule(409, normal, booking, later).path("message").asText().contains("overlapping"));
        assertTripWindow(booking, assignment, start);
        assertEquals(0, id("select count(*) from notification where notification_type='BOOKING_RESCHEDULED'"));
    }

    @Test void confirmedBookingRescheduleReturns404ToAnotherCustomer() throws Exception {
        long booking = createBooking(normal, start);
        long assignment = confirm(booking, driverId, vehicleId, start);
        schedule(404, company, booking, start.plusDays(1));
        assertTripWindow(booking, assignment, start);
    }

    private JsonNode schedule(int status, MockHttpSession actor, long booking, LocalDateTime pickup) throws Exception {
        return expect(status, actor, body(patch("/api/bookings/" + booking + "/customer/schedule"),
                Map.of("scheduledAt", pickup.toString())));
    }

    @Test void uc011ConfirmedCancellationFreesPairAndOnlyNotifiesOnce() throws Exception {
        long booking = createBooking(normal, start);
        long assignment = confirm(booking, driverId, vehicleId, start);
        db.update("""
                insert into booking_vehicle_hold(booking_id,vehicle_id,start_at,end_at,expires_at)
                values (?,?,?,?,?)
                """, booking, vehicleId, start, start.plusHours(2), start.plusHours(1));
        expect(200, normal, post("/api/bookings/" + booking + "/cancel"));
        expect(200, normal, post("/api/bookings/" + booking + "/cancel"));
        assertEquals(1, id("""
                select count(*) from booking b join driver_assignment a using (booking_id)
                where b.booking_id=? and a.assignment_id=? and b.booking_status='CANCELLED'
                  and a.assignment_status='CANCELLED' and a.driver_id=? and a.vehicle_id=?
                """, booking, assignment, driverId, vehicleId));
        assertEquals(1, id("select count(*) from trip_milestone_event where assignment_id=? and milestone='CANCELLED'", assignment));
        assertEquals(0, id("select count(*) from booking_vehicle_hold where booking_id=?", booking));
        String reference = "BK-%06d".formatted(booking);
        assertEquals(1, id("select count(*) from notification where notification_type='BOOKING_CANCELLED' and recipient_role='ADMIN' and entity_id=?", booking));
        assertEquals(1, id("select count(*) from notification where notification_type='BOOKING_CANCELLED' and driver_id=? and position(? in notification_message)>0", driverId, reference));
        assertEquals(1, id("select count(*) from notification where notification_type='BOOKING_CANCELLED' and recipient_role='CUSTOMER' and recipient_id=? and position(? in notification_message)>0", customer, reference));
        long replacement = createBooking(company, start);
        assertTrue(confirm(replacement, driverId, vehicleId, start) > 0);
    }

    @Test void uc011ConfirmedCancellationInsideNoticeLeavesTripUnchanged() throws Exception {
        var pickup = withinNotice();
        long booking = createBooking(normal, pickup);
        long assignment = confirm(booking, driverId, vehicleId, pickup);
        var response = expect(409, normal, post("/api/bookings/" + booking + "/cancel"));
        assertNotice(response);
        assertTripWindow(booking, assignment, pickup);
        assertEquals(0, id("select count(*) from notification where notification_type='BOOKING_CANCELLED'"));
    }

    @Test void uc011ConfirmedCancellationRejectsAnotherCustomerAndStartedTrip() throws Exception {
        long booking = createBooking(normal, start);
        long assignment = confirm(booking, driverId, vehicleId, start);
        expect(404, company, post("/api/bookings/" + booking + "/cancel"));
        assertTripWindow(booking, assignment, start);
        status(assignment, "OTW", 200);
        assertNotice(expect(409, normal, post("/api/bookings/" + booking + "/cancel")));
        assertEquals(1, id("select count(*) from driver_assignment where assignment_id=? and assignment_status='OTW'", assignment));
        for (String terminal : List.of("IN_PROGRESS", "COMPLETED", "REJECTED", "CANCELLED")) {
            long blocked = fixtureBooking(customer, terminal, start.plusDays(1));
            assertNotice(expect(409, normal, post("/api/bookings/" + blocked + "/cancel")));
            assertEquals(1, id("select count(*) from booking where booking_id=? and booking_status=?", blocked, terminal));
        }
    }

    @Test void confirmedReschedulingRequiresNoticeForOldAndNewPickup() throws Exception {
        var soon = withinNotice();
        long booking = createBooking(normal, soon);
        long assignment = confirm(booking, driverId, vehicleId, soon);
        assertNotice(schedule(409, normal, booking, start));
        assertTripWindow(booking, assignment, soon);
        long later = createBooking(normal, start);
        long laterAssignment = confirm(later, driverId, vehicleId, start);
        assertNotice(schedule(409, normal, later, soon.plusHours(5)));
        assertTripWindow(later, laterAssignment, start);
        assertEquals(0, id("select count(*) from notification where notification_type='BOOKING_RESCHEDULED'"));
    }

    private LocalDateTime withinNotice() {
        return LocalDateTime.now(ZoneId.of("Asia/Kuala_Lumpur")).plusHours(12).withNano(0);
    }

    private void assertNotice(JsonNode response) {
        var message = response.path("message").asText();
        assertTrue(message.contains("24 hours") && message.contains("contact Admin"));
    }

    @Test void cardProofReviewAndNewReceiptUseOnlyPaymentAmount() throws Exception {
        long booking = fixtureBooking(customer, "COMPLETED", start);
        var submission = expect(201, normal, multipart("/api/payments/bookings/" + booking).file(file("proof"))
                .param("amount", "50").param("paymentMethod", "CARD"));
        long payment = submission.path("paymentId").asLong();
        assertFalse(submission.has("surchargeAmount"));
        assertEquals(1, id("select count(*) from payment where payment_id=? and amount=50 and surcharge_amount=0", payment));
        review(payment, admin, 200);
        assertEquals(1, id("select count(*) from documents where payment_id=? and document_type='RECEIPT' and amount=50", payment));

        long legacyBooking = fixtureBooking(customer, "COMPLETED", start.plusDays(1));
        long legacy = id("""
                insert into payment(booking_id,amount,surcharge_amount,payment_method,payment_status)
                values (?,50,3,'CARD','PENDING_VERIFICATION') returning payment_id
                """, legacyBooking);
        review(legacy, admin, 200);
        assertEquals(1, id("select count(*) from payment where payment_id=? and surcharge_amount=3", legacy));
        assertEquals(1, id("select count(*) from documents where payment_id=? and document_type='RECEIPT' and amount=50", legacy));
    }

    private void assertTripWindow(long booking, long assignment, LocalDateTime pickup) {
        assertEquals(1, id("""
                select count(*) from booking b join driver_assignment a using (booking_id)
                where b.booking_id=? and a.assignment_id=? and a.driver_id=? and a.vehicle_id=?
                  and b.booking_status='CONFIRMED' and a.assignment_status='ASSIGNED'
                  and b.booking_tripdate+b.booking_pickuptime=? and a.scheduled_end_at=?
                """, booking, assignment, driverId, vehicleId, pickup, pickup.plusHours(2)));
    }

    @Test void categoryEndpointReportsAllFiveScopeLimits() throws Exception {
        var categories = expect(200, admin, get("/api/vehicles/categories"));
        var expected = Map.of("SEDAN", 3, "MPV", 5, "VAN_10_SEATER", 8, "VAN_14_SEATER", 11, "VAN_18_SEATER", 15);
        var actual = new HashMap<String, Integer>();
        for (var category : categories) {
            var key = category.path("categoryKey").asText();
            if (expected.containsKey(key)) actual.put(key, category.path("maxPassengerCapacity").asInt());
        }
        assertEquals(expected, actual);
    }

    @Test void corporateRegistrationAndProfileAllowMissingRegistrationNumber() throws Exception {
        var data = Map.of("type", "COMPANY", "companyName", "TEST- No number", "companyAddress", "TEST- Address",
                "contactPerson", "TEST- Contact", "email", email("no-number"), "phone", "0123456777",
                "password", password, "confirmPassword", password);
        expect(201, null, body(post("/api/auth/mobile/register"), data));
        var owner = login("no-number", "CUSTOMER");
        expect(200, owner, body(put("/api/customers/me"),
                Map.of("name", "TEST- Contact", "email", email("no-number"), "companyName", "TEST- Updated company")));
        long customerId = id("select cust_id from customer where cust_email=?", email("no-number"));
        expect(200, admin, body(put("/api/customers/" + customerId),
                Map.of("custName", "TEST- Contact", "custEmail", email("no-number"), "companyName", "TEST- Edited by Admin")));
        assertEquals(1, id("select count(*) from customer where cust_email=? and company_registration_number is null",
                email("no-number")));
    }

    @Test void dashboardAvailabilityExcludesOnlyActiveDriversCurrentlyOnATrip() throws Exception {
        long booking = createBooking(normal, start);
        long assignment = confirm(booking, driverId, vehicleId, start);
        assertEquals(2, expect(200, admin, get("/api/dashboard/stats")).path("availableDrivers").asInt());
        status(assignment, "OTW", 200);
        var stats = expect(200, admin, get("/api/dashboard/stats"));
        assertEquals(2, stats.path("activeDrivers").asInt());
        assertEquals(1, stats.path("availableDrivers").asInt());
        db.update("update driver set driver_status='DISABLED' where driver_id=?", otherDriverId);
        assertEquals(0, expect(200, admin, get("/api/dashboard/stats")).path("availableDrivers").asInt());
    }

}
