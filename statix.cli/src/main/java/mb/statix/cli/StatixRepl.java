package mb.statix.cli;

import java.io.IOException;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.metaborg.core.MetaborgException;
import org.metaborg.core.language.ILanguageImpl;
import org.metaborg.spoofax.core.syntax.JSGLRParserConfiguration;
import org.metaborg.spoofax.core.unit.ISpoofaxAnalyzeUnit;
import org.metaborg.spoofax.core.unit.ISpoofaxInputUnit;
import org.metaborg.spoofax.core.unit.ISpoofaxParseUnit;

public class StatixRepl {

    private final Statix STX;

    public StatixRepl(Statix stx) {
        this.STX = stx;
    }

    public void run(String file) throws MetaborgException, IOException {
        final Terminal terminal = TerminalBuilder.builder().build();
        final LineReader reader =
                LineReaderBuilder.builder().terminal(terminal).option(LineReader.Option.AUTO_FRESH_LINE, true).build();
        final ILanguageImpl lang = STX.context.language();
        final JSGLRParserConfiguration config = new JSGLRParserConfiguration("CommandLine");
        while(true) {
            final String line;
            try {
                line = reader.readLine("> ");
            } catch(UserInterruptException e) {
                continue;
            } catch(EndOfFileException e) {
                return;
            }
            final ISpoofaxInputUnit inputUnit = STX.S.unitService.inputUnit(line, lang, null, config);
            final ISpoofaxParseUnit parseUnit;
            try {
                parseUnit = STX.cli.parse(inputUnit, lang);
            } catch(MetaborgException e) {
                continue;
            }
            if(!parseUnit.success()) {
                STX.cli.printMessages(STX.msgStream, parseUnit.messages());
                continue;
            }
            terminal.writer().println(STX.S.strategoCommon.toString(parseUnit.ast()));
            final ISpoofaxAnalyzeUnit analyzeUnit;
            try {
                analyzeUnit = STX.cli.analyze(parseUnit, STX.context);
            } catch(MetaborgException e) {
                continue;
            }
            if(!analyzeUnit.success()) {
                STX.cli.printMessages(STX.msgStream, analyzeUnit.messages());
                continue;
            }
        }
    }

}