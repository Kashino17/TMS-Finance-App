from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker
from tms.config import settings

engine = create_engine(f"sqlite:///{settings.db_path}", echo=False)
SessionLocal = sessionmaker(bind=engine)


def get_db():
    with Session(engine) as session:
        yield session
