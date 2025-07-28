package home.ejaz.ledger.forms.calc;

import com.ezylang.evalex.EvaluationException;
import com.ezylang.evalex.Expression;
import com.ezylang.evalex.data.EvaluationValue;
import com.ezylang.evalex.parser.ParseException;
import home.ejaz.ledger.Config;
import home.ejaz.ledger.FormMenu;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class FormCalc extends JDialog {
  private FormMenu parent;
  private int gap = Config.getGap();
  private JLabel result = new JLabel("...");
  private JTextArea expr = new JTextArea();
  private JButton calc = new JButton("Calc");
  private JButton clear = new JButton("Clear");
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
          ex.printStackTrace();
        }
      });

      init = true;
    }
  }

  public FormCalc(FormMenu parent) {
    super(parent);

    this.parent = parent;
    init();

    JPanel main = new JPanel();
    main.setLayout(new BorderLayout(gap, gap));
    main.setBorder(BorderFactory.createEmptyBorder(gap, gap, gap, gap));

    main.add(result, BorderLayout.NORTH);
    main.add(new JScrollPane(expr), BorderLayout.CENTER);

    JPanel btnPanel = new JPanel();
    btnPanel.setLayout(new FlowLayout(FlowLayout.CENTER, gap, gap));
    btnPanel.add(clear);
    btnPanel.add(calc);

    main.add(btnPanel, BorderLayout.SOUTH);

    getContentPane().setLayout(new BorderLayout());
    getContentPane().add(main, BorderLayout.CENTER);

    setTitle("Calculator");
    setSize(250, 200);
    setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
    setLocationRelativeTo(null);
  }
}
