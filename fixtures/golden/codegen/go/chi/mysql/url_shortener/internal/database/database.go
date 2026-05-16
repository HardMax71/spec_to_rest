package database

import (
	"context"
	"database/sql"

	_ "github.com/go-sql-driver/mysql"
	"github.com/uptrace/bun"
	"github.com/uptrace/bun/dialect/mysqldialect"
)

func Connect(ctx context.Context, dsn string) (*bun.DB, error) {
	sqldb, err := sql.Open("mysql", dsn)
	if err != nil {
		return nil, err
	}
	db := bun.NewDB(sqldb, mysqldialect.New())
	if err := db.PingContext(ctx); err != nil {
		_ = db.Close()
		return nil, err
	}
	return db, nil
}
