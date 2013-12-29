package jargs.test.gnu;

//javase imports
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

//JUnit4 imports
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

//jargs imports
import jargs.gnu.CmdLineParser;
import jargs.gnu.CmdLineParser.IllegalOptionValueException;
import jargs.gnu.CmdLineParser.OptionValueParser;

public class CustomOptionTestCase {

    @Test
    public void testCustomOption() throws Exception {
        Calendar calendar = Calendar.getInstance();
        CmdLineParser parser = new CmdLineParser();
        CmdLineParser.Option<Date> date = parser.addUserDefinedOption('d', "date", shortDateParser,
            "enter date");
        parser.parse(new String[]{"-d", "11/03/2003"}, Locale.UK);
        Date d = date.getValue();
        calendar.setTime(d);
        assertEquals(11, calendar.get(Calendar.DAY_OF_MONTH));
        assertEquals(Calendar.MARCH, calendar.get(Calendar.MONTH));
        assertEquals(2003, calendar.get(Calendar.YEAR));
        parser.parse(new String[]{"-d", "11/03/2003"}, Locale.US);
        d = date.getValue();
        calendar.setTime(d);
        assertEquals(3, calendar.get(Calendar.DAY_OF_MONTH));
        assertEquals(Calendar.NOVEMBER, calendar.get(Calendar.MONTH));
        assertEquals(2003, calendar.get(Calendar.YEAR));
    }

    @Test
    public void testIllegalCustomOption() throws Exception {
        CmdLineParser parser = new CmdLineParser();
        @SuppressWarnings("unused")
        CmdLineParser.Option<Date> date = parser.addUserDefinedOption('d', "date", shortDateParser,
            "enter date");
        try {
            parser.parse(new String[]{"-d", "foobar"}, Locale.US);
            fail("Expected IllegalOptionValueException");
        }
        catch (CmdLineParser.IllegalOptionValueException e) {
            // pass
        }
    }

    /**
     * A custom type of command line option corresponding to a short date value, e.g. .
     */
    private static OptionValueParser<Date> shortDateParser = new OptionValueParser<Date>() {
        @Override
        public Date parse(String arg, Locale locale) throws IllegalOptionValueException {
            try {
                DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.SHORT, locale);
                return dateFormat.parse(arg);
            }
            catch (ParseException e) {
                throw new CmdLineParser.IllegalOptionValueException(this, arg);
            }
        }
    };

}
