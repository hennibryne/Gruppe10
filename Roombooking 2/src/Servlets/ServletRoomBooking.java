package Servlets;

import Classes.Email.EmailTemplates;
import Classes.Email.EmailUtil;
import Classes.Email.TLSEmail;
import Classes.Order;
import Tools.DbFunctionality;
import Tools.DbTool;

import javax.mail.Session;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.ParseException;

@WebServlet(name = "Servlets.ServletRoomBooking", urlPatterns = {"/Servlets.ServletRoomBooking"})
public class ServletRoomBooking extends AbstractPostServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try (PrintWriter out = response.getWriter()) {
            //prints the beginning of a  html-page.
            printNav(out);
            //retrieves the value of the Reserve a Room button
            String action = request.getParameter("action").toLowerCase();
            //TODO: Må ordne intersects; en booking med 10:30-11:00 og en annen med 11:00-11:30 returnerer at det overlapper
            if(action.contains("reserve")) {
                System.out.println("Reserve started");
                // Nødvendige klasser for database samhandling og funksjonalitet
                DbTool dbTool = new DbTool();
                Connection connection = dbTool.dbLogIn(out);
                DbFunctionality dbFunctionality = new DbFunctionality();

                // Hent roomID, Timestamp_start og _end for å sjekke reservasjonen
                String formRoomID = request.getParameter("Reserve_Room_ID");
                int roomID = Integer.parseInt(formRoomID);
                String timestampStart = request.getParameter("Reserve_Timestamp_start");
                String timestampEnd = request.getParameter("Reserve_Timestamp_end");
                Order order = new Order(timestampStart, timestampEnd);

                //TODO create db method to retrieve epost with userID from db.
                TLSEmail tlsEmail = new TLSEmail();

                ResultSet orders = dbFunctionality.getOrdersFromRoom(roomID, timestampStart.substring(0, 10), connection);
                System.out.println("Resultset recieved");
                // Lag og sett en boolean til true,
                boolean available = true;
                int iterations = 0;
                // og sjekk order opp mot alle andre reservasjoner.
                while(orders.next()) {
                    System.out.println("iterations: " + ++iterations);
                    Timestamp t1 = orders.getTimestamp("Timestamp_start");
                    Timestamp t2 = orders.getTimestamp("Timestamp_end");
                    Order other = new Order(t1, t2);
                    // Hvis order overlapper med noen settes available til false, og vi avslutter while-løkka
                    if(order.intersects(other)) {
                        available = false;
                        break;
                    }
                }
                // Hvis det er ledig etter hele while-løkka,
                if(available) {
                    // henter vi orderID, lager Order objektet på nytt og legger det til databasen.
                    int orderID = dbFunctionality.getOrderID(connection);
                    // TODO ADD AUTOMATIC USERID
                    order = new Order(orderID, 5, roomID, timestampStart, timestampEnd);
                    dbFunctionality.addOrder(order, connection);
                    // Etter reservasjonen er lagt til i databasen sender vi en kvittering på epost.
                    Session session = tlsEmail.NoReplyEmail("trymerlend@hotmail.no");
                    EmailUtil confirmationEmail = new EmailUtil();
                    String receipt = EmailTemplates.getBookingReceipt();
                    String body = EmailTemplates.bookingConfirmation("Trym",order);
                    confirmationEmail.sendEmail(session,"trymerlend@hotmail.no",receipt,body);
                } else {
                    String errorMessage = "Sorry, that time and room is already taken.";
                    // Hvis ikke returneres en error til brukeren
                    // TODO: Returner en error til brukeren om rommet er opptatt ved tidspunktet valgt
                    System.out.println(errorMessage);
                    out.println(errorMessage);
                }

            }
        } catch (SQLException | ParseException e) {
            e.printStackTrace();
        }
    }
}
