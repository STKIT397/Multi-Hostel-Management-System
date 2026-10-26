package com.hostelmanagement.controller;

import com.hostelmanagement.service.AccessService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.sql.Date;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class DataController {
 private final JdbcTemplate db; private final AccessService access;
 public DataController(JdbcTemplate db, AccessService access){this.db=db;this.access=access;}
 private void req(HttpServletRequest r,String...roles){access.require(r,roles);}
 private void required(Map<String,Object>b,String...keys){for(String k:keys)if(b.get(k)==null||b.get(k).toString().trim().isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,k+" is required.");}
 private int num(Object x,String label){try{return Integer.parseInt(String.valueOf(x));}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,label+" must be a whole number.");}}
 private Map<String,Object> one(String sql,Object...args){var a=db.queryForList(sql,args);if(a.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Record not found.");return a.get(0);}
 private ResponseStatusException conflict(String msg){return new ResponseStatusException(HttpStatus.CONFLICT,msg);}
 private void ensureWardenHostel(HttpServletRequest r,int hostelId){Integer n=db.queryForObject("SELECT COUNT(*) FROM aishwarya.manage WHERE hostel_id=? AND name=?",Integer.class,hostelId,access.wardenName(r));if(n==null||n==0)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"This hostel is not assigned to your warden account.");}
 private void ensureWardenStudent(HttpServletRequest r,int studentId){Integer n=db.queryForObject("SELECT COUNT(*) FROM aishwarya.enroll e JOIN aishwarya.manage m ON m.hostel_id=e.hostel_id JOIN aishwarya.student s ON s.student_id=e.student_id WHERE e.student_id=? AND m.name=? AND COALESCE(s.status,'ACTIVE')='ACTIVE'",Integer.class,studentId,access.wardenName(r));if(n==null||n==0)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"This student is not actively allocated to your hostel.");}


 @GetMapping("/dashboard")
 public Map<String, Object> dashboard(HttpServletRequest r) {
     String role = access.role(r);
     Map<String, Object> m = new LinkedHashMap<>();

     if (role.equals("ADMIN")) {
         m.put("hostels", count("SELECT COUNT(*) FROM aishwarya.hostel"));
         m.put("wardens", count("SELECT COUNT(*) FROM aishwarya.warden"));
         m.put("students", count("SELECT COUNT(*) FROM aishwarya.student"));
         m.put("rooms", count("SELECT COUNT(*) FROM aishwarya.rooms"));

     } else if (role.equals("WARDEN")) {
         String w = access.wardenName(r);

         if (w == null) {
             throw new ResponseStatusException(
                 HttpStatus.FORBIDDEN,
                 "Warden login is not linked to a warden record."
             );
         }

         m.put("hostels", db.queryForObject(
             "SELECT COUNT(*) FROM aishwarya.manage WHERE name = ?",
             Integer.class, w
         ));

         m.put("rooms", db.queryForObject(
             "SELECT COUNT(*) " +
             "FROM aishwarya.rooms r " +
             "JOIN aishwarya.manage m ON m.hostel_id = r.hostel_id " +
             "WHERE m.name = ?",
             Integer.class, w
         ));

         // Count active students allocated to this warden's hostel,
         // plus active students who have not yet been allocated a room.
         m.put("students", db.queryForObject(
             "SELECT COUNT(DISTINCT s.student_id) " +
             "FROM aishwarya.student s " +
             "WHERE COALESCE(s.status, 'ACTIVE') = 'ACTIVE' " +
             "AND (" +
             "    EXISTS (" +
             "        SELECT 1 " +
             "        FROM aishwarya.enroll e " +
             "        JOIN aishwarya.manage mg ON mg.hostel_id = e.hostel_id " +
             "        WHERE e.student_id = s.student_id " +
             "        AND mg.name = ?" +
             "    ) " +
             "    OR NOT EXISTS (" +
             "        SELECT 1 " +
             "        FROM aishwarya.enroll e2 " +
             "        WHERE e2.student_id = s.student_id" +
             "    )" +
             ")",
             Integer.class, w
         ));

         m.put("complaints", db.queryForObject(
             "SELECT COUNT(DISTINCT c.comment_id) " +
             "FROM aishwarya.student_comments c " +
             "JOIN aishwarya.enroll e ON e.student_id = c.student_id " +
             "JOIN aishwarya.manage mg ON mg.hostel_id = e.hostel_id " +
             "WHERE mg.name = ?",
             Integer.class, w
         ));

     } else {
         Integer id = access.studentId(r);

         if (id == null) {
             throw new ResponseStatusException(
                 HttpStatus.FORBIDDEN,
                 "Student login is not linked to a student record."
             );
         }

         m.put("fees", db.queryForObject(
             "SELECT COUNT(*) FROM aishwarya.fees WHERE student_id = ?",
             Integer.class, id
         ));

         m.put("complaints", db.queryForObject(
             "SELECT COUNT(*) FROM aishwarya.student_comments WHERE student_id = ?",
             Integer.class, id
         ));

         m.put("allocation", db.queryForList(
             "SELECT h.name AS hostel_name, e.room_id, r.floor_no, " +
             "r.type AS room_type " +
             "FROM aishwarya.enroll e " +
             "JOIN aishwarya.hostel h ON h.hostel_id = e.hostel_id " +
             "JOIN aishwarya.rooms r " +
             "ON r.hostel_id = e.hostel_id AND r.room_id = e.room_id " +
             "WHERE e.student_id = ?",
             id
         ));
     }

     return m;
 }

 private int count(String sql) {
     Integer n = db.queryForObject(sql, Integer.class);
     return n == null ? 0 : n;
 }

 // HOSTELS
 @GetMapping("/hostels") public List<Map<String,Object>> hostels(HttpServletRequest r){String role=access.role(r);if(role.equals("ADMIN"))return db.queryForList("SELECT hostel_id,name,address FROM aishwarya.hostel ORDER BY hostel_id");if(role.equals("WARDEN"))return db.queryForList("SELECT h.hostel_id,h.name,h.address FROM aishwarya.hostel h JOIN aishwarya.manage m ON m.hostel_id=h.hostel_id WHERE m.name=? ORDER BY h.hostel_id",access.wardenName(r));throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Students cannot list all hostels.");}
 @PostMapping("/hostels") public Map<String,String> addHostel(@RequestBody Map<String,Object>b,HttpServletRequest r){req(r,"ADMIN");required(b,"hostel_id","name","address");db.update("INSERT INTO aishwarya.hostel(hostel_id,name,address) VALUES(?,?,?)",num(b.get("hostel_id"),"hostel_id"),b.get("name").toString().trim(),b.get("address").toString().trim());return ok("Hostel added.");}
 @PutMapping("/hostels/{id}") public Map<String,String> editHostel(@PathVariable int id,@RequestBody Map<String,Object>b,HttpServletRequest r){req(r,"ADMIN");required(b,"name","address");if(db.update("UPDATE aishwarya.hostel SET name=?,address=? WHERE hostel_id=?",b.get("name").toString().trim(),b.get("address").toString().trim(),id)==0)throw notFound();return ok("Hostel updated.");}
 @DeleteMapping("/hostels/{id}") public Map<String,String> delHostel(@PathVariable int id,HttpServletRequest r){req(r,"ADMIN");try{if(db.update("DELETE FROM aishwarya.hostel WHERE hostel_id=?",id)==0)throw notFound();return ok("Hostel deleted.");}catch(DataIntegrityViolationException e){throw conflict("Hostel is linked to rooms or records. Reassign/remove linked records first.");}}

 // WARDENS
 @GetMapping("/wardens") public List<Map<String,Object>> wardens(HttpServletRequest r){req(r,"ADMIN");return db.queryForList("SELECT name,phone_no,address FROM aishwarya.warden ORDER BY name");}
 @PostMapping("/wardens") public Map<String,String> addWarden(@RequestBody Map<String,Object>b,HttpServletRequest r){req(r,"ADMIN");required(b,"name","phone_no","address");db.update("INSERT INTO aishwarya.warden(name,phone_no,address) VALUES(?,?,?)",b.get("name").toString().trim(),b.get("phone_no").toString().trim(),b.get("address").toString().trim());return ok("Warden added.");}
 @PutMapping("/wardens/{name}") public Map<String,String> editWarden(@PathVariable String name,@RequestBody Map<String,Object>b,HttpServletRequest r){req(r,"ADMIN");required(b,"phone_no","address");if(db.update("UPDATE aishwarya.warden SET phone_no=?,address=? WHERE name=?",b.get("phone_no").toString().trim(),b.get("address").toString().trim(),name)==0)throw notFound();return ok("Warden updated. Name is the existing primary key and is not edited here.");}
 @DeleteMapping("/wardens/{name}") public Map<String,String> delWarden(@PathVariable String name,HttpServletRequest r){req(r,"ADMIN");try{if(db.update("DELETE FROM aishwarya.warden WHERE name=?",name)==0)throw notFound();return ok("Warden deleted.");}catch(DataIntegrityViolationException e){throw conflict("Warden is assigned to a hostel or linked to a login. Remove/reassign the link first.");}}

 // STUDENTS
 @GetMapping("/students")
 public List<Map<String, Object>> students(HttpServletRequest r) {

     String role = access.role(r);

     if (role.equals("WARDEN")) {

         String wardenName = access.wardenName(r);

         if (wardenName == null) {
             throw new ResponseStatusException(
                 HttpStatus.FORBIDDEN,
                 "Warden login is not linked to a warden record."
             );
         }

         return db.queryForList(
             "SELECT DISTINCT s.student_id, s.name, " +
             "s.mobile_no, s.address, s.status " +
             "FROM aishwarya.student s " +
             "WHERE COALESCE(s.status, 'ACTIVE') = 'ACTIVE' " +
             "AND ( " +
             "    EXISTS ( " +
             "        SELECT 1 " +
             "        FROM aishwarya.enroll e " +
             "        JOIN aishwarya.manage m " +
             "          ON m.hostel_id = e.hostel_id " +
             "        WHERE e.student_id = s.student_id " +
             "          AND m.name = ? " +
             "    ) " +
             "    OR NOT EXISTS ( " +
             "        SELECT 1 " +
             "        FROM aishwarya.enroll e2 " +
             "        WHERE e2.student_id = s.student_id " +
             "    ) " +
             ") " +
             "ORDER BY s.student_id",
             wardenName
         );
     }

     throw new ResponseStatusException(
         HttpStatus.FORBIDDEN,
         "Only wardens can manage student records."
     );
 }
 @PostMapping("/students") public Map<String,String> addStudent(@RequestBody Map<String,Object>b,HttpServletRequest r){req(r,"WARDEN");required(b,"student_id","name","mobile_no","address");db.update("INSERT INTO aishwarya.student(student_id,name,mobile_no,address,status) VALUES(?,?,?,?, 'ACTIVE')",num(b.get("student_id"),"student_id"),b.get("name").toString().trim(),b.get("mobile_no").toString().trim(),b.get("address").toString().trim());return ok("Student added. Allocate the student to a room next.");}
 @PutMapping("/students/{id}") public Map<String,String> editStudent(@PathVariable int id,@RequestBody Map<String,Object>b,HttpServletRequest r){String role=access.role(r);if(role.equals("WARDEN")){ensureWardenStudent(r,id);}else if(!(role.equals("STUDENT")&&Objects.equals(access.studentId(r),id))){throw new ResponseStatusException(HttpStatus.FORBIDDEN,"You cannot update this student.");}required(b,"name","mobile_no","address");if(db.update("UPDATE aishwarya.student SET name=?,mobile_no=?,address=? WHERE student_id=?",b.get("name").toString().trim(),b.get("mobile_no").toString().trim(),b.get("address").toString().trim(),id)==0)throw notFound();return ok("Student updated.");}
 @DeleteMapping("/students/{id}") @Transactional public Map<String,String> delStudent(@PathVariable int id,HttpServletRequest r){req(r,"WARDEN");String w=access.wardenName(r);Map<String,Object>a=one("SELECT e.hostel_id,e.room_id FROM aishwarya.enroll e JOIN aishwarya.manage m ON m.hostel_id=e.hostel_id JOIN aishwarya.student s ON s.student_id=e.student_id WHERE e.student_id=? AND m.name=? AND COALESCE(s.status,'ACTIVE')='ACTIVE' FOR UPDATE OF e",id,w);db.update("DELETE FROM aishwarya.enroll WHERE hostel_id=? AND room_id=? AND student_id=?",a.get("hostel_id"),a.get("room_id"),id);db.update("UPDATE aishwarya.rooms SET occupied=(SELECT COUNT(*) FROM aishwarya.enroll WHERE hostel_id=? AND room_id=?) WHERE hostel_id=? AND room_id=?",a.get("hostel_id"),a.get("room_id"),a.get("hostel_id"),a.get("room_id"));db.update("UPDATE aishwarya.student SET status='PASSED_OUT' WHERE student_id=?",id);return ok("Student marked as passed out. Fee and complaint history has been preserved.");}

 // ROOMS
 @GetMapping("/rooms") public List<Map<String,Object>> rooms(HttpServletRequest r){req(r,"WARDEN");String sql="SELECT r.hostel_id,h.name AS hostel_name,r.room_id,r.floor_no,r.capacity,r.occupied,r.type FROM aishwarya.rooms r JOIN aishwarya.hostel h ON h.hostel_id=r.hostel_id JOIN aishwarya.manage m ON m.hostel_id=r.hostel_id WHERE m.name=? ORDER BY r.hostel_id,r.room_id";return db.queryForList(sql,access.wardenName(r));}
 @PostMapping("/rooms") public Map<String,String> addRoom(@RequestBody Map<String,Object>b,HttpServletRequest r){req(r,"WARDEN");required(b,"hostel_id","room_id","floor_no","capacity","type");int h=num(b.get("hostel_id"),"hostel_id"),cap=num(b.get("capacity"),"capacity");ensureWardenHostel(r,h);if(cap<1)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Capacity must be positive.");db.update("INSERT INTO aishwarya.rooms(hostel_id,room_id,floor_no,capacity,occupied,type) VALUES(?,?,?,?,0,?)",h,num(b.get("room_id"),"room_id"),num(b.get("floor_no"),"floor_no"),cap,b.get("type").toString().trim());return ok("Room added.");}
 @PutMapping("/rooms/{hostelId}/{roomId}") public Map<String,String> editRoom(@PathVariable int hostelId,@PathVariable int roomId,@RequestBody Map<String,Object>b,HttpServletRequest r){req(r,"WARDEN");required(b,"floor_no","capacity","type");ensureWardenHostel(r,hostelId);int cap=num(b.get("capacity"),"capacity");Integer occupied=db.queryForObject("SELECT occupied FROM aishwarya.rooms WHERE hostel_id=? AND room_id=?",Integer.class,hostelId,roomId);if(occupied==null)throw notFound();if(cap<1||cap<occupied)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Capacity cannot be lower than the current occupied count.");if(db.update("UPDATE aishwarya.rooms SET floor_no=?,capacity=?,type=? WHERE hostel_id=? AND room_id=?",num(b.get("floor_no"),"floor_no"),cap,b.get("type").toString().trim(),hostelId,roomId)==0)throw notFound();return ok("Room updated.");}
 @DeleteMapping("/rooms/{hostelId}/{roomId}") public Map<String,String> delRoom(@PathVariable int hostelId,@PathVariable int roomId,HttpServletRequest r){req(r,"WARDEN");ensureWardenHostel(r,hostelId);try{if(db.update("DELETE FROM aishwarya.rooms WHERE hostel_id=? AND room_id=?",hostelId,roomId)==0)throw notFound();return ok("Room deleted.");}catch(DataIntegrityViolationException e){throw conflict("Room has student allocations. Deallocate students first.");}}

 // ALLOCATIONS
 private String allocationSql(){return "SELECT e.hostel_id,h.name AS hostel_name,e.room_id,e.student_id,s.name AS student_name FROM aishwarya.enroll e JOIN aishwarya.hostel h ON h.hostel_id=e.hostel_id JOIN aishwarya.student s ON s.student_id=e.student_id JOIN aishwarya.rooms r ON r.hostel_id=e.hostel_id AND r.room_id=e.room_id";}
 @GetMapping("/allocations") public List<Map<String,Object>> allocations(HttpServletRequest r){String role=access.role(r);if(role.equals("WARDEN"))return db.queryForList(allocationSql()+" JOIN aishwarya.manage m ON m.hostel_id=e.hostel_id WHERE m.name=? ORDER BY e.hostel_id,e.room_id",access.wardenName(r));if(role.equals("STUDENT")){Integer id=access.studentId(r);if(id==null)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Student login is not linked.");return db.queryForList(allocationSql()+" WHERE e.student_id=?",id);}throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only wardens can manage allocations.");}
 @PostMapping("/allocations") @Transactional public Map<String,String> addAllocation(@RequestBody Map<String,Object>b,HttpServletRequest r){req(r,"WARDEN");required(b,"hostel_id","room_id","student_id");int h=num(b.get("hostel_id"),"hostel_id"),room=num(b.get("room_id"),"room_id"),student=num(b.get("student_id"),"student_id");ensureWardenHostel(r,h);Map<String,Object>x=one("SELECT capacity,occupied FROM aishwarya.rooms WHERE hostel_id=? AND room_id=? FOR UPDATE",h,room);Integer active=db.queryForObject("SELECT COUNT(*) FROM aishwarya.student WHERE student_id=? AND COALESCE(status,'ACTIVE')='ACTIVE'",Integer.class,student);if(active==null||active==0)throw conflict("Student not found or already passed out.");Integer allocated=db.queryForObject("SELECT COUNT(*) FROM aishwarya.enroll WHERE student_id=?",Integer.class,student);if(allocated!=null&&allocated>0)throw conflict("This student already has an allocation.");Integer actualOccupied=db.queryForObject("SELECT COUNT(*) FROM aishwarya.enroll WHERE hostel_id=? AND room_id=?",Integer.class,h,room);if(actualOccupied!=null&&actualOccupied>=((Number)x.get("capacity")).intValue())throw conflict("This room is full.");db.update("INSERT INTO aishwarya.enroll(hostel_id,room_id,student_id) VALUES(?,?,?)",h,room,student);db.update("UPDATE aishwarya.rooms SET occupied=(SELECT COUNT(*) FROM aishwarya.enroll WHERE hostel_id=? AND room_id=?) WHERE hostel_id=? AND room_id=?",h,room,h,room);return ok("Student allocated.");}
 @DeleteMapping("/allocations/{hostelId}/{roomId}/{studentId}") @Transactional public Map<String,String> delAllocation(@PathVariable int hostelId,@PathVariable int roomId,@PathVariable int studentId,HttpServletRequest r){req(r,"WARDEN");ensureWardenHostel(r,hostelId);if(db.update("DELETE FROM aishwarya.enroll WHERE hostel_id=? AND room_id=? AND student_id=?",hostelId,roomId,studentId)==0)throw notFound();db.update("UPDATE aishwarya.rooms SET occupied=(SELECT COUNT(*) FROM aishwarya.enroll WHERE hostel_id=? AND room_id=?) WHERE hostel_id=? AND room_id=?",hostelId,roomId,hostelId,roomId);return ok("Allocation removed.");}

 // FEES
 @GetMapping("/fees") public List<Map<String,Object>> fees(HttpServletRequest r){String role=access.role(r);String base="SELECT f.transaction_id,f.student_id,s.name AS student_name,f.amount,f.status,f.date FROM aishwarya.fees f JOIN aishwarya.student s ON s.student_id=f.student_id";if(role.equals("STUDENT")){Integer id=access.studentId(r);if(id==null)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Student login is not linked.");return db.queryForList("SELECT transaction_id,student_id,amount,status,date FROM aishwarya.fees WHERE student_id=? ORDER BY date DESC",id);}if(role.equals("WARDEN"))return db.queryForList(base+" JOIN aishwarya.enroll e ON e.student_id=f.student_id JOIN aishwarya.manage m ON m.hostel_id=e.hostel_id WHERE m.name=? ORDER BY f.date DESC",access.wardenName(r));throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only wardens can manage fee records.");}
 @PostMapping("/fees") public Map<String,String> addFee(@RequestBody Map<String,Object>b,HttpServletRequest r){req(r,"WARDEN");required(b,"transaction_id","student_id","amount","status","date");int student=num(b.get("student_id"),"student_id");ensureWardenStudent(r,student);BigDecimal amount;try{amount=new BigDecimal(b.get("amount").toString());}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Amount must be numeric.");}if(amount.signum()<0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Amount cannot be negative.");db.update("INSERT INTO aishwarya.fees(transaction_id,student_id,amount,status,date) VALUES(?,?,?,?,?)",num(b.get("transaction_id"),"transaction_id"),student,amount,b.get("status").toString(),Date.valueOf(b.get("date").toString()));return ok("Fee record added.");}
 @PutMapping("/fees/{transactionId}/{studentId}") public Map<String,String> editFee(@PathVariable int transactionId,@PathVariable int studentId,@RequestBody Map<String,Object>b,HttpServletRequest r){req(r,"WARDEN");ensureWardenStudent(r,studentId);required(b,"amount","status","date");BigDecimal amount=new BigDecimal(b.get("amount").toString());if(amount.signum()<0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Amount cannot be negative.");if(db.update("UPDATE aishwarya.fees SET amount=?,status=?,date=? WHERE transaction_id=? AND student_id=?",amount,b.get("status").toString(),Date.valueOf(b.get("date").toString()),transactionId,studentId)==0)throw notFound();return ok("Fee record updated.");}
 @DeleteMapping("/fees/{transactionId}/{studentId}") public Map<String,String> delFee(@PathVariable int transactionId,@PathVariable int studentId,HttpServletRequest r){req(r,"WARDEN");ensureWardenStudent(r,studentId);if(db.update("DELETE FROM aishwarya.fees WHERE transaction_id=? AND student_id=?",transactionId,studentId)==0)throw notFound();return ok("Fee record deleted.");}

 // COMPLAINTS
 @GetMapping("/complaints") public List<Map<String,Object>> complaints(HttpServletRequest r){String role=access.role(r);String cols="c.comment_id,c.student_id,s.name AS student_name,c.type,c.status";try{db.queryForList("SELECT description FROM aishwarya.student_comments LIMIT 0");cols+=",c.description";}catch(Exception ignored){}String base="SELECT "+cols+" FROM aishwarya.student_comments c JOIN aishwarya.student s ON s.student_id=c.student_id";if(role.equals("STUDENT")){Integer id=access.studentId(r);if(id==null)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Student login is not linked.");return db.queryForList(base+" WHERE c.student_id=? ORDER BY c.comment_id",id);}if(role.equals("WARDEN"))return db.queryForList(base+" JOIN aishwarya.enroll e ON e.student_id=c.student_id JOIN aishwarya.manage m ON m.hostel_id=e.hostel_id WHERE m.name=? ORDER BY c.comment_id",access.wardenName(r));throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only wardens and the complaint owner can view complaints.");}
 @PostMapping("/complaints") public Map<String,String> addComplaint(@RequestBody Map<String,Object>b,HttpServletRequest r){req(r,"STUDENT");required(b,"comment_id","type");Integer id=access.studentId(r);if(id==null)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Student login is not linked.");String description=String.valueOf(b.getOrDefault("description",""));try{db.update("INSERT INTO aishwarya.student_comments(comment_id,student_id,type,status,description) VALUES(?,?,?,'Pending',?)",num(b.get("comment_id"),"comment_id"),id,b.get("type").toString().trim(),description);}catch(org.springframework.jdbc.BadSqlGrammarException e){db.update("INSERT INTO aishwarya.student_comments(comment_id,student_id,type,status) VALUES(?,?,?,'Pending')",num(b.get("comment_id"),"comment_id"),id,b.get("type").toString().trim());}return ok("Complaint submitted.");}
 @PutMapping("/complaints/{commentId}/{studentId}") public Map<String,String> editComplaint(@PathVariable int commentId,@PathVariable int studentId,@RequestBody Map<String,Object>b,HttpServletRequest r){req(r,"WARDEN");required(b,"status");String status=b.get("status").toString().trim();if(!List.of("Pending","In Progress","Resolved").contains(status))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Status must be Pending, In Progress, or Resolved.");int n=db.update("UPDATE aishwarya.student_comments c SET status=? WHERE c.comment_id=? AND c.student_id=? AND EXISTS(SELECT 1 FROM aishwarya.enroll e JOIN aishwarya.manage m ON m.hostel_id=e.hostel_id WHERE e.student_id=c.student_id AND m.name=?)",status,commentId,studentId,access.wardenName(r));if(n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Complaint not found or not linked to your assigned hostel.");return ok("Complaint status updated.");}
 @GetMapping("/student-profile") public Map<String,Object> studentProfile(HttpServletRequest r){req(r,"STUDENT");Integer id=access.studentId(r);if(id==null)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Student login is not linked.");return one("SELECT student_id,name,mobile_no,address FROM aishwarya.student WHERE student_id=?",id);}
 private Map<String,String> ok(String message){return Map.of("message",message);} private ResponseStatusException notFound(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Record not found.");}
}
