CREATE TABLE IF NOT EXISTS accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    login VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    birthdate DATE NOT NULL,
    balance INT NOT NULL
);

COMMENT ON TABLE accounts IS 'Таблица аккаунтов пользователей';
COMMENT ON COLUMN accounts.id IS 'Уникальный идентификатор записи';
COMMENT ON COLUMN accounts.login IS 'Логин пользователя (уникальный)';
COMMENT ON COLUMN accounts.name IS 'Фамилия и имя пользователя';
COMMENT ON COLUMN accounts.birthdate IS 'Дата рождения в формате YYYY-MM-DD';
COMMENT ON COLUMN accounts.balance IS 'Текущий баланс счёта в виртуальных деньгах';