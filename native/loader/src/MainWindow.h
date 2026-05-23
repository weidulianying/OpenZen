#pragma once

#include <QMainWindow>
#include <QString>
#include <QVector>

class QTableWidget;
class QTimer;
class QLabel;

namespace loader {
class MainWindow : public QMainWindow {
    Q_OBJECT
public:
    explicit MainWindow(QWidget* parent = nullptr);

private slots:
    void refreshNow();
    void onRowDoubleClicked(int row, int column);

private:
    void buildUi();
    void styleApp();

    QTableWidget* table_   = nullptr;
    QTimer*       timer_   = nullptr;
    QLabel*       status_  = nullptr;
    QLabel*       hint_    = nullptr;

    struct Row {
        unsigned long pid;
        QString title;
        QString cls;
    };
    QVector<Row> rows_;
};
} // namespace loader
