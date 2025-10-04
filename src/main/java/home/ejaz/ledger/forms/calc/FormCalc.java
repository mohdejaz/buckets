package home.ejaz.ledger.forms.calc;

import com.ezylang.evalex.Expression;
import com.ezylang.evalex.data.EvaluationValue;
import home.ejaz.ledger.BucketsListener;
import home.ejaz.ledger.Config;
import home.ejaz.ledger.FormMenu;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;

public class FormCalc extends JPanel {
  private final Logger logger = Logger.getLogger(FormCalc.class);

  private final JLabel result = new JLabel("...");
  private final JTextArea expr = new JTextArea();
  private final JButton calc = new JButton("Calc");
  private final JButton clear = new JButton("Clear");
  private boolean init = false;

  private void clear() {
    result.setText("...");
    expr.setText("");
  }

  public void init() {
    if (!init) {
      result.setHorizontalAlignment(JLabel.TRAILING);
      expr.setLineWrap(true);
      Font font = result.getFont();
      result.setFont(new Font(font.getName(), font.getStyle(), font.getSize() * 2));

      clear.addActionListener(e -> {
        clear();
      });

      calc.addActionListener(e -> {
        Expression expression = new Expression(expr.getText());
        try {
          EvaluationValue value = expression.evaluate();
          result.setText("" + value.getNumberValue());
        } catch (Exception ex) {
          logger.warn("Error", ex);
        }
      });

      init = true;
    }
  }

  public FormCalc(BucketsListener parent) {
    init();

    JPanel main = new JPanel();
    int gap = Config.getGap();
    main.setLayout(new BorderLayout(gap, gap));
    main.setBorder(BorderFactory.createEmptyBorder(gap, gap, gap, gap));

    main.add(result, BorderLayout.NORTH);
    main.add(new JScrollPane(expr), BorderLayout.CENTER);

    JPanel btnPanel = new JPanel();
    btnPanel.setLayout(new FlowLayout(FlowLayout.CENTER, gap, gap));
    btnPanel.add(clear);
    btnPanel.add(calc);

    main.add(btnPanel, BorderLayout.SOUTH);

    setLayout(new BorderLayout());
    add(main, BorderLayout.CENTER);

    /*
    setTitle("Calculator");
    setSize(250, 200);
    setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
    setLocationRelativeTo(null);
     */
  }
}
