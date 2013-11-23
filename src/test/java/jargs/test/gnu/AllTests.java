package jargs.test.gnu;

//JUnit4 imports
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({
    CmdLineParserTestCase.class
    ,CustomOptionTestCase.class
  }
)
public class AllTests {  
}