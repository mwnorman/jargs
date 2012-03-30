package jargs.gnu;

//javase imports
import java.io.PrintStream;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Largely GNU-compatible command-line options parser. Has short (-v) and long-form (--verbose)
 * option support, and also allows options with associated values (-d 2, --debug 2, --debug=2).
 * Option processing can be explicitly terminated by the argument '--'.
 * 
 * @author Mike Norman
 * @version $Revision: 1.11 $
 * @see jargs.examples.gnu.OptionTest
 * 
 *      List of authors:
 *      2001-2003   Steve Purcell
 *      2002        Vidar Holen
 *      2002        Michal Ceresna
 *      2005        Ewan Mellor
 *      2011        M. Amber Hassaan
 *                  - generics
 *                  - replace Vector/Hashtable w List/ArrayList/Map/HashMap
 *      2012        Mike Norman
 *                  - support optional arguments: create a new <Void> OptionValueParser
 *                    (useful for --help or --verbose options that dont need any actual
 *                    values)
 *                  - support adding default value for an option
 *                  - support an 'isFound()' test method for an option
 *                  - move to JUnit4
 */
public class CmdLineParser {

    /**
     * Base class for exceptions that may be thrown when options are parsed
     */
    public static abstract class OptionException extends Exception {
        private static final long serialVersionUID = 1L;
        OptionException(String msg) {
            super(msg);
        }
    }

    /**
     * Thrown when the parsed command-line contains an option that is not recognised.
     * <code>getMessage()</code> returns an error string suitable for reporting the error to the
     * user (in English).
     */
    public static class UnknownOptionException extends OptionException {
        private static final long serialVersionUID = 1L;
        private String optionName = null;
        UnknownOptionException(String optionName) {
            this(optionName, "Unknown option '" + optionName + "'");
        }
        UnknownOptionException(String optionName, String msg) {
            super(msg);
            this.optionName = optionName;
        }
        /**
         * @return the name of the option that was unknown (e.g. "-u")
         */
        public String getOptionName() {
            return this.optionName;
        }
    }

    /**
     * Thrown when the parsed commandline contains multiple concatenated short options, such as
     * -abcd, where one is unknown. <code>getMessage()</code> returns an english human-readable
     * error string.
     * 
     * @author Vidar Holen
     */
    public static class UnknownSuboptionException extends UnknownOptionException {
        private static final long serialVersionUID = 1L;
        private char suboption;
        UnknownSuboptionException(String option, char suboption) {
            super(option, "Illegal option: '" + suboption + "' in '" + option + "'");
            this.suboption = suboption;
        }
        public char getSuboption() {
            return suboption;
        }
    }

    /**
     * Thrown when the parsed commandline contains multiple concatenated short options, such as
     * -abcd, where one or more requires a value. <code>getMessage()</code> returns an english
     * human-readable error string.
     * 
     * @author Vidar Holen
     */
    public static class NotFlagException extends UnknownOptionException {
        private static final long serialVersionUID = 1L;
        private char notflag;
        NotFlagException(String option, char unflaggish) {
            super(option, "Illegal option: '" + option + "', '" + unflaggish + "' requires a value");
            notflag = unflaggish;
        }
        /**
         * @return the first character which wasn't a boolean (e.g 'c')
         */
        public char getOptionChar() {
            return notflag;
        }
    }

    /**
     * Thrown when an illegal or missing value is given by the user for an option that takes a
     * value. <code>getMessage()</code> returns an error string suitable for reporting the error to
     * the user (in English).
     */
    public static class IllegalOptionValueException extends OptionException {
        private static final long serialVersionUID = 1L;
        private OptionValueParser<?> option;
        private String value;
        public IllegalOptionValueException(OptionValueParser<?> optionValueParser, String value) {
            super("Illegal value '" + value + "' for option parser" + optionValueParser);
            this.option = optionValueParser;
            this.value = value;
        }
        /**
         * @return the name of the option whose value was illegal (e.g. "-u")
         */
        public OptionValueParser<?> getOption() {
            return this.option;
        }
        /**
         * @return the illegal value
         */
        public String getValue() {
            return this.value;
        }
    }

    /**
     * Basically a converter from String into a specific type e.g. Integer, Double or a user defined
     * type
     * 
     * @author ahassaan
     * 
     * @param <T>
     */
    public static interface OptionValueParser<T> {
        public T parse(String val, Locale locale) throws IllegalOptionValueException;
    }

    /**
     * Representation of a command-line option
     */
    public static class Option<T> {
        private String shortForm = null;
        private final String longForm;
        private boolean wantsValue;
        private final OptionValueParser<T> valueParser;
        private String defaultArgument = null;
        private Locale locale = null;
        private List<T> values = new ArrayList<T>();
        private final String helpMsg;
        private boolean found = false;
        public Option(String longForm, boolean wantsValue, OptionValueParser<T> valueParser,
            String helpMsg) {
            this(null, longForm, wantsValue, valueParser, helpMsg);
        }
        public Option(char shortForm, String longForm, boolean wantsValue,
            OptionValueParser<T> valueParser, String helpMsg) {
            this(new String(new char[]{shortForm}), longForm, wantsValue, valueParser, helpMsg);
        }
        private Option(String shortForm, String longForm, boolean wantsValue,
            OptionValueParser<T> valueParser, String helpMsg) {
            if (longForm == null) {
                throw new IllegalArgumentException("Null longForm not allowed");
            }
            this.shortForm = shortForm;
            this.longForm = longForm;
            this.wantsValue = wantsValue;
            this.valueParser = valueParser;
            this.helpMsg = (helpMsg == null) ? "" : helpMsg;
        }
        public String shortForm() {
            return this.shortForm;
        }
        public String longForm() {
            return this.longForm;
        }
        /**
         * Tells whether or not this option wants a value
         */
        public boolean wantsValue() {
            return this.wantsValue;
        }
        public void addDefaultArgument(String defaultArgument) {
            this.defaultArgument = defaultArgument;
            this.wantsValue = false;
        }
        public boolean isFound() {
            return found;
        }
        /**
         * @return the parsed value of the given Option, or null if the option was not set
         */
        public final T getValue() {
            return getValue(null);
        }
        /**
         * @param def
         *            default value
         * @return the parsed value of the given Option or default, either provided as argument or
         *         the defaultArgument set on the given Option
         */
        public final T getValue(T def) {
            assert values != null;
            T val = def;
            if (values.isEmpty()) {
                if (defaultArgument != null) {
                    try {
                        val = valueParser.parse(defaultArgument,
                            locale == null ? Locale.getDefault() : locale);
                    }
                    catch (IllegalOptionValueException e) {
                        // ignore
                    }
                }
            }
            else {
                val = values.get(0);
                values.remove(0);
            }
            return val;
        }
        /**
         * @return A List giving the parsed values of all the occurrences of the given Option, or
         *         an empty List if the option was not set.
         */
        public final List<T> getValues() {
            List<T> result = new ArrayList<T>();
            result.addAll(this.values);
            values.clear();
            return result;
        }
        void addValue(String valArg, Locale locale) throws IllegalOptionValueException {
            this.locale = locale;
            T val = null;
            try {
                val = valueParser.parse(valArg, locale);
            }
            catch (IllegalOptionValueException iove) {
                if (defaultArgument != null) {
                    val = valueParser.parse(defaultArgument, locale);
                }
                else {
                    throw iove;
                }
            }
            values.add(val);
        }
        public OptionValueParser<T> getValueParser() {
            return valueParser;
        }
        String getHelpMsg() {
            return String.format("-%s, --%s === %s", shortForm, longForm, helpMsg);
        }
    }
    /**
     * Add the specified Option to the list of accepted options
     */
    protected final <T> Option<T> addOption(Option<T> opt) {
        if (opt.shortForm() != null) {
            this.options.put("-" + opt.shortForm(), opt);
        }
        this.options.put("--" + opt.longForm(), opt);
        return opt;
    }
    /**
     * Convenience method for adding a string option.
     * 
     * @return the new Option
     */
    public final Option<String> addStringOption(char shortForm, String longForm, String helpMsg) {
        return addOption(new Option<String>(shortForm, longForm, true, stringParser, helpMsg));
    }
    /**
     * Convenience method for adding a string option.
     * 
     * @return the new Option
     */
    public final Option<String> addStringOption(String longForm, String helpMsg) {
        return addOption(new Option<String>(longForm, true, stringParser, helpMsg));
    }
    /**
     * Convenience method for adding an integer option.
     * 
     * @return the new Option
     */
    public final Option<Integer> addIntegerOption(char shortForm, String longForm, String helpMsg) {
        return addOption(new Option<Integer>(shortForm, longForm, true, integerParser, helpMsg));
    }
    /**
     * Convenience method for adding an integer option.
     * 
     * @return the new Option
     */
    public final Option<Integer> addIntegerOption(String longForm, String helpMsg) {
        return addOption(new Option<Integer>(longForm, true, integerParser, helpMsg));
    }
    /**
     * Convenience method for adding a long integer option.
     * 
     * @return the new Option
     */
    public final Option<Long> addLongOption(char shortForm, String longForm, String helpMsg) {
        return addOption(new Option<Long>(shortForm, longForm, true, longParser, helpMsg));
    }
    /**
     * Convenience method for adding a long integer option.
     * 
     * @return the new Option
     */
    public final Option<Long> addLongOption(String longForm, String helpMsg) {
        return addOption(new Option<Long>(longForm, true, longParser, helpMsg));
    }
    /**
     * Convenience method for adding a double option.
     * 
     * @return the new Option
     */
    public final Option<Double> addDoubleOption(char shortForm, String longForm, String helpMsg) {
        return addOption(new Option<Double>(shortForm, longForm, true, doubleParser, helpMsg));
    }
    /**
     * Convenience method for adding a double option.
     * 
     * @return the new Option
     */
    public final Option<Double> addDoubleOption(String longForm, String helpMsg) {
        return addOption(new Option<Double>(longForm, true, doubleParser, helpMsg));
    }
    /**
     * Convenience method for adding a boolean option.
     * 
     * @return the new Option
     */
    public final Option<Boolean> addBooleanOption(char shortForm, String longForm, String helpMsg) {
        return addOption(new Option<Boolean>(shortForm, longForm, false, flagParser, helpMsg));
    }
    /**
     * Convenience method for adding a boolean option.
     * 
     * @return the new Option
     */
    public final Option<Boolean> addBooleanOption(String longForm, String helpMsg) {
        return addOption(new Option<Boolean>(longForm, false, flagParser, helpMsg));
    }
    /**
     * Convenience method for adding an optional <Void> option.
     * 
     * @return the new Option
     */
    public final Option<Void> addVoidOption(char shortForm, String longForm, String helpMsg) {
        return addOption(new Option<Void>(shortForm, longForm, false, voidParser, helpMsg));
    }
    /**
     * Convenience method for adding an optional <Void> option.
     * 
     * @return the new Option
     */
    public final Option<Void> addVoidOption(String longForm, String helpMsg) {
        return addOption(new Option<Void>(longForm, false, voidParser, helpMsg));
    }
    /**
     * Allow conversion of option values into user defined types
     * 
     * @param <T>
     * @param shortForm
     * @param longForm
     * @param valueParser
     * @return Option instance
     */
    public <T> Option<T> addUserDefinedOption(char shortForm, String longForm,
        OptionValueParser<T> valueParser, String helpMsg) {
        return addOption(new Option<T>(shortForm, longForm, true, valueParser, helpMsg));
    }
    /**
     * Allow conversion of option values into user defined types
     * 
     * @param <T>
     * @param longForm
     * @param valueParser
     * @return Option instance
     */
    public <T> Option<T> addUserDefinedOption(String longForm, OptionValueParser<T> valueParser,
        String helpMsg) {
        return addOption(new Option<T>(longForm, true, valueParser, helpMsg));
    }
    /**
     * @return the non-option arguments
     */
    public final String[] getRemainingArgs() {
        return this.remainingArgs;
    }
    /**
     * Extract the options and non-option arguments from the given list of command-line arguments.
     * The default locale is used for parsing options whose values might be locale-specific.
     */
    public final void parse(String[] argv) throws IllegalOptionValueException,
        UnknownOptionException {
        // It would be best if this method only threw OptionException, but for
        // backwards compatibility with old user code we throw the two
        // exceptions above instead.
        parse(argv, Locale.getDefault());
    }
    /**
     * Extract the options and non-option arguments from the given list of command-line arguments.
     * The specified locale is used for parsing options whose values might be locale-specific.
     */
    public final void parse(String[] argv, Locale locale) throws IllegalOptionValueException,
        UnknownOptionException {
        // It would be best if this method only threw OptionException, but for
        // backwards compatibility with old user code we throw the two
        // exceptions above instead.
        List<String> otherArgs = new ArrayList<String>();
        int position = 0;
        while (position < argv.length) {
            String curArg = argv[position];
            if (curArg.startsWith("-")) {
                if (curArg.equals("--")) { // end of options
                    position += 1;
                    break;
                }
                String valueArg = null;
                if (curArg.startsWith("--")) { // handle --arg=value
                    int equalsPos = curArg.indexOf("=");
                    if (equalsPos != -1) {
                        valueArg = curArg.substring(equalsPos + 1);
                        curArg = curArg.substring(0, equalsPos);
                    }
                }
                else if (curArg.length() > 2) { // handle -abcd
                    for (int i = 1; i < curArg.length(); i++) {
                        Option<?> opt = this.options.get("-" + curArg.charAt(i));
                        if (opt == null) {
                            throw new UnknownSuboptionException(curArg, curArg.charAt(i));
                        }
                        if (opt.wantsValue()) {
                            throw new NotFlagException(curArg, curArg.charAt(i));
                        }
                        opt.addValue(null, locale);
                        opt.found = true;
                    }
                    position++;
                    continue;
                }
                Option<?> opt = this.options.get(curArg);
                if (opt == null) {
                    throw new UnknownOptionException(curArg);
                }
                if (opt.wantsValue()) {
                    if (valueArg == null) {
                        position += 1;
                        if (position < argv.length) {
                            valueArg = argv[position];
                        }
                    }
                }
                opt.addValue(valueArg, locale);
                opt.found = true;
                position += 1;
            }
            else {
                otherArgs.add(curArg);
                position += 1;
            }
        }
        for (; position < argv.length; ++position) {
            otherArgs.add(argv[position]);
        }
        this.remainingArgs = otherArgs.toArray(new String[otherArgs.size()]);
    }
    /**
     * 
     * @return usage as a string
     */
    public String getUsage() {
        String ret = "";
        for (Option<?> o : options.values()) {
            ret = ret + o.getHelpMsg() + "\n";
        }
        return ret;
    }
    /**
     * print usage to the specified print stream
     * 
     * @param out
     */
    public void printUsage(PrintStream out) {
        out.print(getUsage());
    }
    /**
     * print usage to std err
     */
    public void printUsage() {
        printUsage(System.err);
    }
    private String[] remainingArgs = null;
    // switch to LinkedHashMap so that options are printed in order they are added to the parser
    private Map<String, Option<?>> options = new LinkedHashMap<String, CmdLineParser.Option<?>>(10);

    public static final OptionValueParser<Void> voidParser = new OptionValueParser<Void>() {
        @Override
        public Void parse(String val, Locale locale) throws IllegalOptionValueException {
            return null;
        }
    };
    public static final OptionValueParser<String> stringParser = new OptionValueParser<String>() {
        @Override
        public String parse(String val, Locale locale) throws IllegalOptionValueException {
            if (val == null) {
                throw new IllegalOptionValueException(this, val);
            }
            return new String(val);
        }
    };
    public static final OptionValueParser<Integer> integerParser = new OptionValueParser<Integer>() {
        @Override
        public Integer parse(String val, Locale locale) throws IllegalOptionValueException {
            if (val == null) {
                throw new IllegalOptionValueException(this, val);
            }
            try {
                return new Integer(NumberFormat.getInstance(locale).parse(val).intValue());
            }
            catch (NumberFormatException e) {
                throw new IllegalOptionValueException(this, val);
            }
            catch (ParseException e) {
                throw new IllegalOptionValueException(this, val);
            }
        }
    };

    public static final OptionValueParser<Long> longParser = new OptionValueParser<Long>() {
        @Override
        public Long parse(String val, Locale locale) throws IllegalOptionValueException {
            if (val == null) {
                throw new IllegalOptionValueException(this, val);
            }
            try {
                return new Long(NumberFormat.getInstance(locale).parse(val).longValue());
            }
            catch (NumberFormatException e) {
                throw new IllegalOptionValueException(this, val);
            }
            catch (ParseException e) {
                throw new IllegalOptionValueException(this, val);
            }
        }
    };

    public static final OptionValueParser<Double> doubleParser = new OptionValueParser<Double>() {
        @Override
        public Double parse(String val, Locale locale) throws IllegalOptionValueException {
            if (val == null) {
                throw new IllegalOptionValueException(this, val);
            }
            try {
                return new Double(NumberFormat.getInstance(locale).parse(val).doubleValue());
            }
            catch (NumberFormatException e) {
                throw new IllegalOptionValueException(this, val);
            }
            catch (ParseException e) {
                throw new IllegalOptionValueException(this, val);
            }
        }
    };

    public static final OptionValueParser<Boolean> flagParser = new OptionValueParser<Boolean>() {
        @Override
        public Boolean parse(String val, Locale locale) throws IllegalOptionValueException {
            if (val != null) {
                throw new IllegalOptionValueException(this, val);
            }
            return Boolean.TRUE;
        }
    };

}
